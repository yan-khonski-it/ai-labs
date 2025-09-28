package com.yk.aiagent.openai;

// @formatter:off
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;import java.io.IOException; /**
 * <pre><code>
 {
 "id": "resp_68d5b5d9fb988195ae0e49c60122d04d0ae88fd517d98fe6",
 "object": "response",
 "created_at": 1758836186,
 "status": "completed",
 "background": false,
 "billing": {
 "payer": "developer"
 },
 "error": null,
 "incomplete_details": null,
 "instructions": null,
 "max_output_tokens": null,
 "max_tool_calls": null,
 "model": "gpt-5-2025-08-07",
 "output": [
 {
 "id": "rs_68d5b5dabd6881959cccfb66473444750ae88fd517d98fe6",
 "type": "reasoning",
 "summary": []
 },
 {
 "id": "msg_68d5b5ef10588195a59f90c827d0f41f0ae88fd517d98fe6",
 "type": "message",
 "status": "completed",
 "content": [
 {
 "type": "output_text",
 "annotations": [],
 "logprobs": [],
 "text": "SomeText"
 }
 ],
 "role": "assistant"
 }
 ],
 "parallel_tool_calls": true,
 "previous_response_id": null,
 "prompt_cache_key": null,
 "reasoning": {
 "effort": "medium",
 "summary": null
 },
 "safety_identifier": null,
 "service_tier": "default",
 "store": true,
 "temperature": 1,
 "text": {
 "format": {
 "type": "json_object"
 },
 "verbosity": "medium"
 },
 "tool_choice": "auto",
 "tools": [],
 "top_logprobs": 0,
 "top_p": 1,
 "truncation": "disabled",
 "usage": {
 "input_tokens": 866,
 "input_tokens_details": {
 "cached_tokens": 0
 },
 "output_tokens": 2357,
 "output_tokens_details": {
 "reasoning_tokens": 1728
 },
 "total_tokens": 3223
 },
 "user": null,
 "metadata": {}
 }
 * </code></pre>
 */
// @formatter:on
public final class OpenAiResponseExtractor {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private OpenAiResponseExtractor() {
    throw new AssertionError("Instance is not allowed.");
  }


  /** Returns the assistant's textual payload (or null if not found). */
  public static String extractText(String responseText) {
    if (responseText == null || responseText.isEmpty()) {
      System.out.println("Empty response received from OpenAI API.");
      return null;
    }

    try {
      JsonNode root = OBJECT_MAPPER.readTree(responseText);

      // 1) Prefer top-level "output_text" if present
      JsonNode ot = root.get("output_text");
      if (ot != null && ot.isTextual()) return ot.asText();

      // 2) /v1/responses: output[].type=="message" → content[].type=="output_text".text
      JsonNode output = root.get("output");
      if (output != null && output.isArray()) {
        for (JsonNode item : output) {
          if (!"message".equals(item.path("type").asText())) continue;
          JsonNode content = item.path("content");
          if (content.isArray()) {
            for (JsonNode c : content) {
              if ("output_text".equals(c.path("type").asText())) {
                JsonNode text = c.get("text");
                if (text != null && text.isTextual()) return text.asText();
              }
            }
          }
        }
      }

      // 3) Fallbacks for other shapes (older/alt envelopes)
      JsonNode choices = root.get("choices");
      if (choices != null && choices.isArray() && !choices.isEmpty()) {
        JsonNode m = choices.get(0).path("message").path("content");
        if (m.isTextual()) return m.asText();
        JsonNode t2 = choices.get(0).get("text");
        if (t2 != null && t2.isTextual()) return t2.asText();
      }

      return null;
    } catch (Exception e) {
      return null;
    }
  }
}
