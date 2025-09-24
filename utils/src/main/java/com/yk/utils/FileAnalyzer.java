package com.yk.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FileAnalyzer {

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: java FileAnalyzer <directory>");
      System.exit(1);
    }

    Path startDir = Paths.get(args[0]);
    if (!Files.isDirectory(startDir)) {
      System.err.println("Provided path is not a directory");
      System.exit(1);
    }

    final AtomicInteger totalFiles = new AtomicInteger(0);
    List<FileStats> statsList = new ArrayList<>();

    Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (Files.isRegularFile(file)) {
          try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            long charCount = content.length();
            long wordCount = Arrays.stream(content.split("\\s+"))
                .filter(s -> !s.isEmpty())
                .count();
            statsList.add(new FileStats(file.toString(), wordCount, charCount));
            totalFiles.incrementAndGet();
          } catch (IOException e) {
            System.err.println("Could not read file: " + file + " (" + e.getMessage() + ")");
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });

    if (statsList.isEmpty()) {
      System.out.println("No files found in the directory.");
      return;
    }

    FileStats mostChars = Collections.max(statsList, Comparator.comparingLong(fs -> fs.chars));
    FileStats mostWords = Collections.max(statsList, Comparator.comparingLong(fs -> fs.words));

    System.out.println("File with most characters: " + mostChars.path + " (" + mostChars.chars + ")");
    System.out.println("File with most words: " + mostWords.path + " (" + mostWords.words + ")");
    System.out.println("Total files: " + totalFiles.get());
  }

  static class FileStats {

    String path;
    long words;
    long chars;

    FileStats(String path, long words, long chars) {
      this.path = path;
      this.words = words;
      this.chars = chars;
    }
  }
}
