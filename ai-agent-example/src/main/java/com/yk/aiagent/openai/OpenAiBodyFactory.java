package com.yk.aiagent.openai;

import com.yk.aiagent.utils.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a request body to be sent to OpenAI API.
 */
public final class OpenAiBodyFactory {

  private static final String DEFAULT_MODEL_NAME = "gpt-5";

  private OpenAiBodyFactory() {
    throw new AssertionError("Instance is not allowed.");
  }

  // @formatter:off
  /**
   * <pre><code>
{
    "model": "<MODEL_NAME>",
    "input": [{
        "role": "user",
        "content": [
            { "type": "input_text", "text": "<INSTRUCTIONS>" },
            { "type": "input_text", "text": "```\n<CODE_CONTENT>\n```\n" }
        ]
    }],
    "text": { "format": { "type": "json_object" } }
}
   * </code></pre>
   */
  // @formatter:on
  public static String getBody(String content) {
    String instruction = """
        Analyze the following file content and respond ONLY with a SINGLE valid JSON object, no extra text.
        The JSON must contain exactly these keys: ShortSummary, Summary, MostImportantThings, RelatedFunctionality.
        
        - ShortSummary: 1–2 sentences, very concise.
        - Summary: 3–5 sentences, plain text, max ~500 words.
        - MostImportantThings: array of the key insights.
        - RelatedFunctionality: array of related functions, classes, or features.
        If uncertain, note it briefly within the relevant field.
        
        """;

    String contentAsCodeSection = "```\n" + content + "\n```\n";

    Map<String, Object> bodyAsMap = new LinkedHashMap<>();
    bodyAsMap.put("model", DEFAULT_MODEL_NAME);
    bodyAsMap.put("input", List.of(
        Map.of(
            "role", "user",
            "content", List.of(
                Map.of("type", "input_text", "text", instruction),
                Map.of("type", "input_text", "text", contentAsCodeSection)
            )
        )
    ));

    bodyAsMap.put("text", Map.of("format", Map.of("type", "json_object")));

    return JsonUtils.writeJson(bodyAsMap);
  }

  public static String getModelName() {
    return DEFAULT_MODEL_NAME;
  }
}
