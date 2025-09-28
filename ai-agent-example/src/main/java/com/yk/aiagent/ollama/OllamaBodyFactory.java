package com.yk.aiagent.ollama;

import com.yk.aiagent.utils.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OllamaBodyFactory {

  private OllamaBodyFactory() {
    throw new AssertionError("Instance is not allowed.");
  }

  // @formatter:off
  /**
   * <pre><code>
{
   "model": "%s",
   "messages": [
       { "role": "user", "content": %s }
   ],
   "stream": false
}
   * </code></pre>
   */
  // @formatter:on
  public static String getBody(String content, String model) {
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
    bodyAsMap.put("model", model);
    bodyAsMap.put("messages", List.of(
        Map.of(
            "role", "user",
            "content", instruction + "\n" + contentAsCodeSection
        )
    ));
    bodyAsMap.put("stream", false);

    bodyAsMap.put("text", Map.of("format", Map.of("type", "json_object")));

    return JsonUtils.writeJson(bodyAsMap);
  }
}
