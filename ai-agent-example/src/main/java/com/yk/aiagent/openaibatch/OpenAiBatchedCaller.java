package com.yk.aiagent.openaibatch;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.yk.aiagent.openai.OpenAiBodyFactory;
import com.yk.aiagent.openai.OpenAiResponseExtractor;
import com.yk.aiagent.utils.JsonUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;


/**
 * Submits many Responses API requests at once via the OpenAI Batch API. Flow: 1) enqueue(customId, content) 2) flushAndWait() -> Map<customId, outputText>
 * <p>
 * Each enqueued item becomes one JSONL line: { "custom_id": "...", "method": "POST", "url": "/v1/responses", "body": { ... } }
 */
public class OpenAiBatchedCaller {

  private static final URI FILES_URI = URI.create("https://api.openai.com/v1/files");
  private static final URI BATCHES_URI = URI.create("https://api.openai.com/v1/batches");
  private static final String PURPOSE = "batch";
  private static final String ENDPOINT = "/v1/responses";

  private static final int HTTP_TIMEOUT_SECONDS = 90;
  private static final int POLL_MAX_MINUTES = 60;    // batch completion window is typically up to 24h; tune as needed
  private static final long POLL_BASE_MS = 900;   // polling backoff base
  private static final long POLL_MAX_MS = 8_000; // max polling interval

  private final String apiKey;
  private final String model; // e.g., "gpt-5"
  private final HttpClient http;
  private final Map<String, String> queue = new LinkedHashMap<>(); // customId -> content

  public OpenAiBatchedCaller(String apiKey, String model) {
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    this.model = Objects.requireNonNullElse(model, OpenAiBodyFactory.getModelName());
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  /**
   * Add one item to the next batch. customId must be unique (use relative path, etc.).
   */
  public void enqueue(String customId, String fileContent) {
    if (customId == null || customId.isBlank()) {
      throw new IllegalArgumentException("customId must be non-empty");
    }
    queue.put(customId, fileContent);
  }

  /**
   * Number of queued items.
   */
  public int size() {
    return queue.size();
  }

  /**
   * Clear queue without sending.
   */
  public void clear() {
    queue.clear();
  }

  /**
   * Builds JSONL, uploads it, creates a batch, polls for completion, downloads output JSONL, and returns a map: customId -> extracted output_text (or null on
   * non-2xx or parse fail).
   */
  public Map<String, String> flushAndWait() throws IOException, InterruptedException {
    if (queue.isEmpty()) {
      return Collections.emptyMap();
    }

    // 1) Build JSONL file
    Path jsonl = Files.createTempFile("openai-batch-", ".jsonl");
    try {
      List<String> lines = queue.entrySet().stream()
          .map(e -> oneLineJsonl(e.getKey(), e.getValue()))
          .collect(Collectors.toList());
      Files.write(jsonl, lines, UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

      // 2) Upload file
      String inputFileId = uploadFile(jsonl);

      // 3) Create batch
      String batchId = createBatch(inputFileId);

      // 4) Poll until done
      BatchState state = pollBatch(batchId);

      if (!"completed".equalsIgnoreCase(state.status)) {
        throw new IOException("Batch did not complete successfully. Status=" + state.status + ", error=" + state.errorMessage);
      }
      if (state.outputFileId == null || state.outputFileId.isBlank()) {
        throw new IOException("Completed batch has no output_file_id.");
      }

      // 5) Download output JSONL
      String outputJsonl = downloadFileContent(state.outputFileId);

      // 6) Parse output JSONL into Map<customId, outputText>
      return parseOutputJsonl(outputJsonl);

    } finally {
      try {
        Files.deleteIfExists(jsonl);
      } catch (Exception ignored) {
      }
      queue.clear();
    }
  }

  /**
   * Construct one JSONL line for the batch input.
   */
  private String oneLineJsonl(String customId, String content) {
    // Reuse your existing request body builder to keep behavior identical
    String bodyJson = OpenAiBodyFactoryJsonForModel.getBodyForModel(model, content);

    Map<String, Object> line = new LinkedHashMap<>();
    line.put("custom_id", customId);
    line.put("method", "POST");
    line.put("url", ENDPOINT);
    line.put("body", JsonUtils.readJsonNode(bodyJson)); // ensure it's embedded as JSON, not string

    return JsonUtils.writeJson(line);
  }

  private String uploadFile(Path jsonl) throws IOException, InterruptedException {
    MultipartBodyPublisher mp = MultipartBodyPublisher.newBuilder()
        .addText("purpose", PURPOSE)
        .addFile("file", jsonl, "application/jsonl")
        .build();

    HttpRequest req = HttpRequest.newBuilder()
        .uri(FILES_URI)
        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "multipart/form-data; boundary=" + mp.getBoundary())
        .POST(mp.buildPublisher())
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("File upload failed: " + resp.statusCode() + " " + resp.body());
    }
    var root = JsonUtils.readJsonNode(resp.body());
    return root.path("id").asText();
  }

  private String createBatch(String inputFileId) throws IOException, InterruptedException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("input_file_id", inputFileId);
    body.put("endpoint", ENDPOINT);
    body.put("completion_window", "24h");

    HttpRequest req = HttpRequest.newBuilder()
        .uri(BATCHES_URI)
        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.writeJson(body), UTF_8))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("Create batch failed: " + resp.statusCode() + " " + resp.body());
    }
    var root = JsonUtils.readJsonNode(resp.body());
    return root.path("id").asText();
  }

  private BatchState pollBatch(String batchId) throws IOException, InterruptedException {
    URI statusUri = URI.create("https://api.openai.com/v1/batches/" + batchId);
    long start = System.currentTimeMillis();
    long elapsedMin = 0;
    long sleepMs = POLL_BASE_MS;

    while (elapsedMin <= POLL_MAX_MINUTES) {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(statusUri)
          .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
          .header("Authorization", "Bearer " + apiKey)
          .GET()
          .build();

      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
      if (resp.statusCode() / 100 != 2) {
        throw new IOException("Get batch failed: " + resp.statusCode() + " " + resp.body());
      }
      var root = JsonUtils.readJsonNode(resp.body());
      String status = root.path("status").asText();
      if ("completed".equalsIgnoreCase(status)) {
        String outFileId = root.path("output_file_id").asText(null);
        return new BatchState(status, outFileId, null);
      }
      if ("failed".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) {
        String err = root.path("error").path("message").asText(null);
        return new BatchState(status, null, err);
      }

      // backoff + jitter
      Thread.sleep(sleepMs + ThreadLocalRandom.current().nextLong(200));
      sleepMs = Math.min(POLL_MAX_MS, sleepMs * 2);
      elapsedMin = (System.currentTimeMillis() - start) / 60_000L;
    }

    return new BatchState("timeout", null, "Polling timed out after " + POLL_MAX_MINUTES + " minutes.");
  }

  private String downloadFileContent(String fileId) throws IOException, InterruptedException {
    URI uri = URI.create("https://api.openai.com/v1/files/" + fileId + "/content");
    HttpRequest req = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
        .header("Authorization", "Bearer " + apiKey)
        .GET()
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("Download output failed: " + resp.statusCode() + " " + resp.body());
    }
    return resp.body();
  }

  /**
   * Parse output JSONL from batch into customId -> extracted output_text (null if missing).
   */
  private Map<String, String> parseOutputJsonl(String jsonl) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String line : jsonl.split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      var node = JsonUtils.readJsonNode(line);
      String cid = node.path("custom_id").asText(null);
      var respNode = node.path("response");
      int statusCode = respNode.path("status_code").asInt(0);
      String text;

      if (statusCode / 100 == 2) {
        // Per batch output format: response.body is the usual Responses envelope
        String bodyStr = respNode.path("body").toString();
        text = OpenAiResponseExtractor.extractText(bodyStr);
      } else {
        // keep null to signal failure; caller can log cid
        text = null;
      }
      if (cid != null) {
        out.put(cid, text);
      }
    }
    return out;
  }

  private record BatchState(String status, String outputFileId, String errorMessage) {

  }

  /**
   * Keep using your familiar body shape but allow overriding model for batches. (OpenAiBodyFactory is hard-wired to a default; this helper mirrors it with a
   * model param.)
   */
  private static final class OpenAiBodyFactoryJsonForModel {

    static String getBodyForModel(String model, String content) {
      String instruction = """
          Analyze the following file content and respond ONLY with a SINGLE valid JSON object, no extra text.
          The JSON must contain exactly these keys: ShortSummary, Summary, MostImportantThings, RelatedFunctionality.
          
          - ShortSummary: 1–2 sentences, very concise.
          - Summary: 3–5 sentences, plain text, max ~500 words.
          - MostImportantThings: array of the key insights.
          - RelatedFunctionality: array of related functions, classes, or features.
          If uncertain, note it briefly within the relevant field.
          """;
      String contentAsCodeSection = "```\\n" + content + "\\n```\\n";

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", model);
      body.put("input", List.of(
          Map.of(
              "role", "user",
              "content", List.of(
                  Map.of("type", "input_text", "text", instruction),
                  Map.of("type", "input_text", "text", contentAsCodeSection)
              )
          )
      ));
      body.put("text", Map.of("format", Map.of("type", "json_object")));
      return JsonUtils.writeJson(body);
    }
  }
}

