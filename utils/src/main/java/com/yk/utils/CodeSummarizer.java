package com.yk.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class CodeSummarizer {

  // Pick a model you have access to (adjust as needed).
  private static final String MODEL = "gpt-4.1-mini";
  private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
  // Soft guard to avoid exceeding token limits when sending very large files.
  private static final int MAX_CHARS = 120_000;

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java CodeSummarizer <path-to-source-file>");
      System.exit(1);
    }

    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      System.err.println("Error: OPENAI_API_KEY environment variable is not set.");
      System.exit(2);
    }

    Path filePath = Path.of(args[0]);
    if (!Files.isRegularFile(filePath)) {
      System.err.println("Error: path is not a regular file: " + filePath);
      System.exit(3);
    }

    String code = Files.readString(filePath, StandardCharsets.UTF_8);
    boolean truncated = false;
    if (code.length() > MAX_CHARS) {
      code = code.substring(0, MAX_CHARS);
      truncated = true;
    }

    String userPrompt = buildUserPrompt(code, truncated, filePath.toString());
    String requestBody = """
        {
          "model": "%s",
          "temperature": 0.2,
          "messages": [
            {
              "role": "system",
              "content": "You are a senior software engineer. Analyze source code carefully and answer concisely. Output strictly valid JSON using the exact keys requested and nothing else."
            },
            {
              "role": "user",
              "content": %s
            }
          ]
        }
        """.formatted(MODEL, jsonEscape(userPrompt));

    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(OPENAI_API_URL))
        .timeout(Duration.ofSeconds(90))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() / 100 != 2) {
      System.err.println("OpenAI API error (" + response.statusCode() + "):\n" + response.body());
      System.exit(4);
    }

    // Naive extraction: the API responds with JSON where choices[0].message.content is the model's reply.
    // We keep it simple and print the content directly; it should already be strict JSON per our prompt.
    String content = extractMessageContent(response.body());
    if (content == null || content.isBlank()) {
      System.err.println("Empty response content from model.");
      System.exit(5);
    }

    System.out.println(content.trim());
  }

  private static String buildUserPrompt(String code, boolean truncated, String fileName) {
    return """
        Analyze the following source code file and respond with STRICT JSON ONLY.
        Do not include Markdown, comments, or extra fields.
        Use exactly these keys (keep spelling exactly):
        {
          "ShortSummary": "",
          "Summary": "",
          "MostInportantThings": "",
          "RelatedFunctionality": ""
        }
        
        Guidelines:
        - "ShortSummary": 1–3 sentences plain overview of what the code does.
        - "Summary": A concise paragraph describing behavior, inputs/outputs, major classes/functions, flow.
        - "MostInportantThings": Bullet-style (in a single string, use \\n as separator) of the most critical aspects.
        - "RelatedFunctionality": Describe relationships to other parts (e.g., helper methods, modules, layers), inferred responsibilities and integration points.
        - If any part is uncertain, state the uncertainty briefly.
        - Output must be valid JSON.
        
        File: %s
        %s
        
        ---- BEGIN CODE ----
        %s
        ---- END CODE ----
        """.formatted(
        fileName,
        truncated ? "(Note: Code was truncated for length; base your analysis on visible content.)" : "",
        code
    );
  }

  // Minimal JSON-string escaper for embedding prompt content as a JSON string value.
  private static String jsonEscape(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '\"' -> sb.append("\\\"");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append("\"");
    return sb.toString();
  }

  // Very small JSON extractor for choices[0].message.content (avoids a full JSON lib for brevity).
  // For production, use Jackson/Gson to parse: choices[0].message.content
  private static String extractMessageContent(String responseJson) {
    // Look for: "message":{"role":"assistant","content":"..."}
    // or more safely: find "content":"..."} within "message":
    String marker = "\"message\":";
    int msgIdx = responseJson.indexOf(marker);
    if (msgIdx < 0) {
      return null;
    }

    int contentKey = responseJson.indexOf("\"content\":", msgIdx);
    if (contentKey < 0) {
      return null;
    }

    int firstQuote = responseJson.indexOf('"', contentKey + 10);
    if (firstQuote < 0) {
      return null;
    }

    // Extract a JSON string (handles escapes) — simple state machine:
    StringBuilder out = new StringBuilder();
    boolean escape = false;
    for (int i = firstQuote + 1; i < responseJson.length(); i++) {
      char c = responseJson.charAt(i);
      if (escape) {
        out.append(c);
        escape = false;
      } else if (c == '\\') {
        out.append(c);
        escape = true;
      } else if (c == '"') {
        // end of string
        break;
      } else {
        out.append(c);
      }
    }

    // Unescape basic sequences for output (optional; model already returns JSON string content)
    String contentEscaped = out.toString();
    return contentEscaped
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\");
  }
}
