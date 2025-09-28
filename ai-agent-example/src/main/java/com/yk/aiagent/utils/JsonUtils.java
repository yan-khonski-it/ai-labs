package com.yk.aiagent.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonUtils() {
    throw new AssertionError("Instance is not allowed.");
  }

  public static JsonNode readJsonNode(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse json.", e);
    }
  }

  public static JsonNode readJsonNode(File file) {
    String content = FileUtils.readFile(file);
    return readJsonNode(content);
  }

  public static String writeJson(Object object) {
    try {
      return OBJECT_MAPPER.writeValueAsString(object);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write json.", e);
    }
  }

  public static boolean isValid(String json) {
    try {
      var ignored = OBJECT_MAPPER.readTree(json);
      return true;
    } catch (IOException e) {
      System.out.println("Failed to parse json.");
      e.printStackTrace();
      return false;
    }
  }
}
