package com.yk.aiagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public class FileProcessor {

  private static final int MAX_CHARS = 900_000; // truncate very large files
  private final Path inputRoot;
  private final Path outputRoot;
  private final OpenAICaller caller;

  public FileProcessor(Path inputRoot, Path outputRoot, OpenAICaller caller) {
    this.inputRoot = inputRoot;
    this.outputRoot = outputRoot;
    this.caller = caller;
  }

  public void process(Path file, BasicFileAttributes attrs) throws IOException, InterruptedException {
    Path rel = inputRoot.relativize(file);
    Path copied = outputRoot.resolve(rel);

    Files.createDirectories(copied.getParent());
    Files.copy(file, copied, StandardCopyOption.REPLACE_EXISTING);

    String content = readUtf8(file);
    content = ContentCleaner.stripLeadingCommentHeader(content);
    if (content.length() > MAX_CHARS) {
      content = content.substring(0, MAX_CHARS);
    }

    String analysisJson = caller.analyzeFileContent(file.toString(), content);
    if (analysisJson == null || analysisJson.isBlank()) {
      analysisJson = "{}";
    }

    Path analysed = copied.getParent().resolve(copied.getFileName().toString() + "-analysed.json");
    Files.writeString(analysed, analysisJson, StandardCharsets.UTF_8);
    System.out.println("Analyzed: " + rel + " -> " + outputRoot.relativize(analysed));
  }

  private static String readUtf8(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    return new String(bytes, StandardCharsets.UTF_8); // replaces invalid sequences
  }
}
