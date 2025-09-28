package com.yk.aiagent.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OllamaResponseExtractor {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private OllamaResponseExtractor() {}

  public static String extractText(String responseText) {
    if (responseText == null || responseText.isEmpty()) {
      System.out.println("Empty response received from Ollama.");
      return null;
    }
    
    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseText);
      JsonNode msg = root.path("message").path("content");
      if (msg.isTextual()) {
        return extractJson(msg.asText());
      }
      // fallback: some responses might return "messages"
      JsonNode msgs = root.path("messages");
      if (msgs.isArray() && !msgs.isEmpty()) {
        JsonNode last = msgs.get(msgs.size() - 1).path("content");
        if (last.isTextual()) {
          return extractJson(last.asText());
        }
      }
    } catch (Exception ignore) {}
    return responseText; // fallback raw
  }

  /**
   * ```json
   * {
   *   "ShortSummary": "abc"
   * }
   * ```
   **/
  private static String extractJson(String text) {
    if (text.startsWith("```json")) {
      if (text.endsWith("```")) {
        text = text.replace("```json", "");
        text = text.replace("```", "");
      } else {
        System.out.println("Ollama returned malformed response, which is not a valid JSON.");
        return null;
      }
    }

    return text;
  }
}
