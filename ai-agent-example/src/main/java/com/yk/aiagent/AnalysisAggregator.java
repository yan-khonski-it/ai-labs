package com.yk.aiagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.yk.aiagent.utils.JsonUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// WIP
public class AnalysisAggregator {

  private static final String ANALYSED_SUFFIX = "-analysed.json";
  private static final String AGGREGATED_FILE = "aggregated.json";

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java AnalysisAggregator <rootDir>");
      System.exit(1);
    }

    Path root = Paths.get(args[0]);
    if (!Files.isDirectory(root)) {
      System.err.println("Not a directory: " + root);
      System.exit(2);
    }

    AggregatingVisitor visitor = new AggregatingVisitor();
    Files.walkFileTree(root, visitor);
  }

  // Walks the tree and aggregates per-directory analysed files.
  private static final class AggregatingVisitor extends SimpleFileVisitor<Path> {

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      String directoryName = dir.getFileName().toString();
      if (directoryName.equalsIgnoreCase("excluded")) {
        System.out.printf("Skipping filtered directory: %s.\n", directoryName);
        // No need to check nested directories because they are also excluded.
        return FileVisitResult.SKIP_SUBTREE;
      }

      File directoryFile = dir.toFile();
      File[] files = directoryFile.listFiles();
      if (files == null || files.length == 0) {
        System.out.printf("Skipping directory: %s because no files in directory.\n", directoryName);
        return FileVisitResult.SKIP_SUBTREE;
      }

      if (Arrays.stream(files).anyMatch(file -> file.getName().equals(AGGREGATED_FILE))) {
        System.out.printf("Skipping directory: %s it has been aggregated before.\n", directoryName);
        return FileVisitResult.CONTINUE;
      }

      // So the  aggregated file output gets deterministic.
      Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

      List<FileRecord> analysedFilesContent = new ArrayList<>(files.length);
      for (File file : files) {
        if (!file.isFile() || !file.getName().endsWith(ANALYSED_SUFFIX)) {
          continue;
        }

        JsonNode fileContent = JsonUtils.readJsonNode(file);
        analysedFilesContent.add(new FileRecord(file.getName(), fileContent));
      }

      if (analysedFilesContent.isEmpty()) {
        System.out.printf("The current directory: %s does not have analysed files.\n", directoryName);
        return FileVisitResult.CONTINUE;
      }

      String aggregatedResult = JsonUtils.writeJson(analysedFilesContent);

      // Write aggregatedResult into a file dir + AGGREGATED_FILE
      Path aggregatedFilePath = dir.resolve(AGGREGATED_FILE);
      Files.writeString(aggregatedFilePath, aggregatedResult, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      System.out.printf("Finished analysing directory: %s.\n", directoryName);

      return FileVisitResult.CONTINUE;
    }
  }

  record FileRecord(String filename, JsonNode content) {}
}
