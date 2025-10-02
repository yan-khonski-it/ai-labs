package com.yk.aiagent;

import com.yk.aiagent.ollama.OllamaCaller;
import com.yk.aiagent.openai.OpenAICaller;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: java Main <inputDir> <outputDir>");
      System.exit(1);
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args[1]);

    String openAiApiKey = System.getenv("OPENAI_API_KEY_AI_LABS_CODE_ANALYSER");
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      System.err.println("ERROR: Set OPENAI_API_KEY_AI_LABS_CODE_ANALYSER.");
      System.exit(2);
    }

    OpenAICaller caller1 = new OpenAICaller(openAiApiKey);
    OllamaCaller  caller2 = new OllamaCaller("gemma3:12b");
    App app = new App(inputDir, outputDir, caller1);
    app.run();
  }
}