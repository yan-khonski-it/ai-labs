package com.yk.aiagent;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WIP
public class AnalysisAggregator {

  private static final String DEFAULT_AGGREGATE_FILENAME = "_analysed.json";
  private static final String ANALYSED_SUFFIX = "-analysed.json";

  public static void main(String[] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: java AnalysisAggregator <rootDir> [aggregateFileName]");
      System.exit(1);
    }
    Path root = Paths.get(args[0]);
    String aggregateName = (args.length == 2 && !args[1].isBlank())
        ? args[1] : DEFAULT_AGGREGATE_FILENAME;

    if (!Files.isDirectory(root)) {
      System.err.println("Not a directory: " + root);
      System.exit(2);
    }

    AggregatingVisitor visitor = new AggregatingVisitor(root, aggregateName);
    Files.walkFileTree(root, visitor);
  }

  // Walks the tree and aggregates per-directory analysed files.
  private static final class AggregatingVisitor extends SimpleFileVisitor<Path> {
    private final Path root;
    private final String aggregateName;
    private final Map<Path, List<Entry>> byDir = new HashMap<>();

    AggregatingVisitor(Path root, String aggregateName) {
      this.root = root;
      this.aggregateName = aggregateName;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      byDir.put(dir, new ArrayList<>());
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (attrs.isRegularFile() && file.getFileName().toString().endsWith(ANALYSED_SUFFIX)) {
        try {
          String analysis = Files.readString(file, StandardCharsets.UTF_8).trim();
          String analysedName = file.getFileName().toString();
          String originalName = analysedName.substring(0, analysedName.length() - ANALYSED_SUFFIX.length());
          byDir.get(file.getParent()).add(new Entry(originalName, analysis));
        } catch (Exception e) {
          System.out.println("Skipping unreadable file " + root.relativize(file) + " (" + e.getMessage() + ")");
        }
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      List<Entry> entries = byDir.remove(dir);
      if (entries == null || entries.isEmpty()) return FileVisitResult.CONTINUE;

      String rel = root.relativize(dir).toString().replace('\\', '/');
      StringBuilder sb = new StringBuilder(Math.max(4096, entries.size() * 512));
      sb.append("{\n");
      sb.append("  \"directory\": ").append(JsonEscaper.escape(rel.isEmpty() ? "." : rel)).append(",\n");
      sb.append("  \"count\": ").append(entries.size()).append(",\n");
      sb.append("  \"analyses\": [\n");

      for (int i = 0; i < entries.size(); i++) {
        Entry e = entries.get(i);
        sb.append("    { \"file\": ").append(JsonEscaper.escape(e.originalName)).append(", \"analysis\": ");
        // Assume valid JSON; embed raw (object/array/primitive). If you ever need safety, wrap with JsonEscaper.escape(...)
        sb.append(e.analysis);
        sb.append(" }");
        if (i + 1 < entries.size()) sb.append(",");
        sb.append("\n");
      }

      sb.append("  ]\n");
      sb.append("}\n");

      Path out = dir.resolve(aggregateName);
      Files.writeString(out, sb.toString(), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      System.out.println("Wrote " + root.relativize(out));
      return FileVisitResult.CONTINUE;
    }

    private record Entry(String originalName, String analysis) {}
  }
}
