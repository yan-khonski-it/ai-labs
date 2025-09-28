package com.yk.aiagent;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

public class App {

  private final Path inputDir;
  private final Path outputDir;
  private final FileProcessor processor;
  private final AtomicInteger counter = new AtomicInteger(0);

  public App(Path inputDir, Path outputDir, Caller caller) {
    this.inputDir = inputDir;
    this.outputDir = outputDir;
    this.processor = new FileProcessor(inputDir, outputDir, caller);
  }

  public void run() throws IOException {
    if (!Files.isDirectory(inputDir)) {
      throw new IOException("Input is not a directory: " + inputDir);
    }
    Files.createDirectories(outputDir);

    Files.walkFileTree(inputDir, new SimpleFileVisitor<>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        String directoryName = dir.getFileName().toString();
        if (Filter.isDirectoryExcluded(directoryName)) {
          System.out.printf("Skipping directory: %s.\n", directoryName);
          return FileVisitResult.SKIP_SUBTREE;
        }

        Path rel = inputDir.relativize(dir);
        Files.createDirectories(outputDir.resolve(rel));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (Filter.isFileExcluded(file.getFileName().toString())) {
          return FileVisitResult.CONTINUE;
        }

        if (attrs.isRegularFile()) {
          try {
            processor.process(file);
            counter.incrementAndGet();
          } catch (Exception e) {
            System.err.println("ERROR processing " + file + ": " + e.getMessage());
          }
        }
        return FileVisitResult.CONTINUE;
      }

    });

    System.out.println(counter.get() + " files processed");
  }
}
