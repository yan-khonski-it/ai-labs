package com.yk.aiagent;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 3) {
      System.err.println("Usage: java Main <inputDir> <outputDir> [model]");
      System.exit(1);
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args[1]);
    String model = (args.length == 3) ? args[2] : "gpt-5";

    String openAiApiKey = System.getenv("OPENAI_API_KEY_AI_LABS_CODE_ANALYSER");
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      System.err.println("ERROR: Set OPENAI_API_KEY_AI_LABS_CODE_ANALYSER.");
      System.exit(2);
    }

    OpenAICaller caller = new OpenAICaller(openAiApiKey, model);
    App app = new App(inputDir, outputDir, caller);
    app.run();
  }
}