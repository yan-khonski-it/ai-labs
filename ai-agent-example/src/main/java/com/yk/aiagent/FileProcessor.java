package com.yk.aiagent;


import static java.lang.String.format;

import com.yk.aiagent.utils.FileUtils;
import com.yk.aiagent.utils.JsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileProcessor {

  private static final int MAX_CHARS = 900_000; // truncate very large files
  private final Path inputRoot;
  private final Path outputRoot;
  private final Caller caller;

  public FileProcessor(Path inputRoot, Path outputRoot, Caller caller) {
    this.inputRoot = inputRoot;
    this.outputRoot = outputRoot;
    this.caller = caller;
  }

  public void process(Path file) throws IOException, InterruptedException {
    Path rel = inputRoot.relativize(file);
    Path copied = outputRoot.resolve(rel);

    Files.createDirectories(copied.getParent());
    Files.copy(file, copied, StandardCopyOption.REPLACE_EXISTING);

    String filename = file.getFileName().toString();
    if (filename.contains("-analysed")) {
      System.out.printf("Skipping analysed file: %s.\n", filename);
      return;
    }

    String content = FileUtils.readFile(file);
    content = ContentCleaner.stripLeadingCommentHeader(content);
    if (content.length() > MAX_CHARS) {
      System.out.printf("File: %s content is too large. Stripping it...", filename);
      content = content.substring(0, MAX_CHARS);
    }

    String modelResponse = caller.call(content);
    if (modelResponse == null || modelResponse.isBlank()) {
      System.out.printf("Failed to get analysis for the file: %s. Got empty response.", filename);
      return;
    }

    if (!JsonUtils.isValid(modelResponse)) {
      System.out.printf("Failed to get analysis for the file: %s. Got response that is not a valid JSON.\n\n%s\n\n", filename, modelResponse);
      return;
    }

    Path analysed = copied.getParent().resolve(copied.getFileName().toString() + "-analysed-2.json");
    Files.writeString(analysed, modelResponse, StandardCharsets.UTF_8);
    System.out.println("Analyzed: " + rel + " -> " + outputRoot.relativize(analysed));
  }
}
