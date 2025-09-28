package com.yk.aiagent.utils;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtils {

  private FileUtils() {
    throw new AssertionError("Instance is not allowed.");
  }

  public static String readFile(File file) {
    return readFile(file.toPath());
  }

  public static String readFile(Path path) {
    String content;
    try {
      content = Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(format("Failed to read file: %s.", path.getFileName().toString()), e);
    }

    // Optional BOM strip
    content = content.startsWith("\uFEFF") ? content.substring(1) : content;

    // Normalize newlines
    content = content.replace("\r\n", "\n").replace('\r', '\n');

    return content;
  }
}
