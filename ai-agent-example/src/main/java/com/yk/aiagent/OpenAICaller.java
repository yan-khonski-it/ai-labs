package com.yk.aiagent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenAICaller {

  private static final String API_URL = "https://api.openai.com/v1/responses";
  private static final int TIMEOUT_SECONDS = 90;

  private final String openAiApiKey;
  private final String model;
  private final HttpClient http;

  public OpenAICaller(String openAiApiKey, String model) {
    this.openAiApiKey = openAiApiKey;
    this.model = model;
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2) // will reuse connections
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  public String analyzeFileContent(String filename, String content) throws IOException, InterruptedException {
    String instruction = "Analyze the following file content and reply with a SINGLE valid JSON object ONLY with these keys: "
        + "ShortSummary, Summary, MostImportantThings, RelatedFunctionality.\n"
        + "Be concise. If uncertain, note it briefly within fields.\n"
        + "File: " + filename;

    String body = """
            {
              "model": "%s",
              "input": [{
                "role": "user",
                "content": [
                  { "type": "input_text", "text": %s },
                  { "type": "input_text", "text": %s }
                ]
              }],
              "text": { "format": { "type": "json_object" } }
            }
            """.formatted(
        model,
        JsonEscaper.escape(instruction),
        JsonEscaper.escape(content)
    );

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(API_URL))
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .header("Authorization", "Bearer " + openAiApiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    System.out.println("Sending request to openAi. File: " + filename);
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("OpenAI API error (" + resp.statusCode() + "): " + resp.body());
    }

    System.out.println("Response from openAi. File: " + filename);

    // Prefer output_text (convenience) if present, else first "text" string, else full envelope
    String extracted = extractOutputText(resp.body());
    return (extracted != null && !extracted.isBlank()) ? extracted.trim() : resp.body();
  }

  private static String extractOutputText(String json) {
    // 1) "output_text":"...json..."
    String key = "\"output_text\":";
    int idx = json.indexOf(key);
    if (idx >= 0) {
      int start = json.indexOf('"', idx + key.length());
      if (start >= 0) return readJsonString(json, start);
    }
    // 2) fallback: first "text":"..." (may be under output/choices)
    key = "\"text\":";
    int t = json.indexOf(key);
    if (t >= 0) {
      int start = json.indexOf('"', t + key.length());
      if (start >= 0) return readJsonString(json, start);
    }
    return null;
  }

  // Reads a JSON string starting at the quote index, with basic un-escaping.
  private static String readJsonString(String src, int firstQuote) {
    StringBuilder out = new StringBuilder();
    boolean esc = false;
    for (int i = firstQuote + 1; i < src.length(); i++) {
      char c = src.charAt(i);
      if (esc) {
        switch (c) {
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'u' -> {
            if (i + 4 < src.length()) {
              String hex = src.substring(i + 1, i + 5);
              try {
                out.append((char) Integer.parseInt(hex, 16));
                i += 4;
              } catch (NumberFormatException ignore) {}
            }
          }
          default -> out.append(c);
        }
        esc = false;
      } else if (c == '\\') {
        esc = true;
      } else if (c == '"') {
        break;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
