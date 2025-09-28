package com.yk.aiagent.ollama;

import com.yk.aiagent.Caller;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OllamaCaller implements Caller {
  private static final String API_URL = "http://127.0.0.1:11434/api/chat";
  private static final int TIMEOUT_SECONDS = 90;
  private static final int MAX_RETRIES = 3;

  private final String model;
  private final HttpClient http;

  public OllamaCaller(String model) {
    this.model = model;
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1) // Ollama works fine with HTTP/1.1
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  @Override
  public String call(String content) throws IOException, InterruptedException {
    // Simple chat body: one user message, no streaming
    String body = OllamaBodyFactory.getBody(content, model);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(API_URL))
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();

    int attempt = 0;
    IOException lastIo = null;
    InterruptedException lastInt = null;

    while (attempt++ <= MAX_RETRIES) {
      try {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int sc = resp.statusCode();

        if (sc / 100 == 2) {
          return OllamaResponseExtractor.extractText(resp.body());
        }

        // Retry on 429 / 5xx
        if (sc == 429 || sc / 100 == 5) {
          long sleepMs = retryDelayMs(resp.headers(), attempt);
          if (attempt <= MAX_RETRIES) {
            Thread.sleep(sleepMs);
            continue;
          }
        }

        throw new IOException("Ollama API error (" + sc + "): " + resp.body());

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        lastInt = ie;
        break;
      } catch (IOException ioe) {
        lastIo = ioe;
        if (attempt <= MAX_RETRIES) {
          try {
            Thread.sleep(backoffMs(attempt));
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            lastInt = ie;
            break;
          }
        }
      }
    }

    if (lastInt != null) throw lastInt;
    throw lastIo;
  }

  private static long retryDelayMs(HttpHeaders headers, int attempt) {
    return headers.firstValue("retry-after")
        .map(v -> {
          try { return Long.parseLong(v) * 1000L; }
          catch (NumberFormatException e) { return backoffMs(attempt); }
        })
        .orElseGet(() -> backoffMs(attempt));
  }

  private static long backoffMs(int attempt) {
    long base = (long) Math.min(30_000, Math.pow(2, attempt) * 500L);
    long jitter = (long) (Math.random() * 250);
    return base + jitter;
  }
}
