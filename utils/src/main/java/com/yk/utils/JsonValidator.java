package com.yk.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonValidator {

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: java JsonValidator <directory>");
      System.exit(1);
    }

    Path startDir = Paths.get(args[0]);
    if (!Files.isDirectory(startDir)) {
      System.err.println("Provided path is not a directory");
      System.exit(1);
    }

    final AtomicInteger totalJsonFiles = new AtomicInteger(0);
    final AtomicInteger validJsonFiles = new AtomicInteger(0);
    final List<String> invalidJsonFilePaths = Collections.synchronizedList(new ArrayList<>()); // Thread-safe list

    // Jackson ObjectMapper for JSON validation
    final ObjectMapper objectMapper = new ObjectMapper();

    Files.walkFileTree(startDir, new SimpleFileVisitor<>() {

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (!Files.isRegularFile(file)) {
          return FileVisitResult.CONTINUE;
        }

        if (!file.toString().endsWith(".json")) {
          return FileVisitResult.CONTINUE;
        }

        if (Filter.isFileExcluded(file.toFile().getName())) {
          return FileVisitResult.CONTINUE;
        }

        totalJsonFiles.incrementAndGet();
        try {
          String content = Files.readString(file, StandardCharsets.UTF_8);
          // Attempt to read the JSON content. If it throws an exception, it's invalid.
          objectMapper.readTree(content);
          validJsonFiles.incrementAndGet();
        } catch (IOException e) {
          invalidJsonFilePaths.add(file + " (" + e.getMessage() + ")");
          System.err.println("Invalid JSON file: " + file + " (" + e.getMessage() + ")");
        }

        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (Filter.isDirectoryExcluded(dir.toFile().getName())) {
          return FileVisitResult.SKIP_SUBTREE;
        }

        return FileVisitResult.CONTINUE;
      }
    });

    System.out.println("\n--- JSON Validation Summary ---");
    System.out.println("Total JSON files found: " + totalJsonFiles.get());
    System.out.println("Valid JSON files: " + validJsonFiles.get());
    System.out.println("Invalid JSON files: " + invalidJsonFilePaths.size());

    if (!invalidJsonFilePaths.isEmpty()) {
      System.out.println("\nDetails of invalid JSON files:");
      invalidJsonFilePaths.forEach(System.out::println);
    } else if (totalJsonFiles.get() > 0) {
      System.out.println("All JSON files found are valid!");
    } else {
      System.out.println("No JSON files found in the directory.");
    }
  }
}
