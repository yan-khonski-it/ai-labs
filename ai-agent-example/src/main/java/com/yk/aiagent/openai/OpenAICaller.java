package com.yk.aiagent.openai;


import com.yk.aiagent.Caller;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenAICaller implements Caller {

  private static final String API_URL = "https://api.openai.com/v1/responses";
  private static final int TIMEOUT_SECONDS = 90;
  private static final int MAX_RETRIES = 3;

  private final String openAiApiKey;
  private final HttpClient http;

  public OpenAICaller(String openAiApiKey) {
    this.openAiApiKey = openAiApiKey;
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2) // will reuse connections
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  @Override
  public String call(String content) throws IOException, InterruptedException {
    String body = OpenAiBodyFactory.getBody(content);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(API_URL))
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .header("Authorization", "Bearer " + openAiApiKey)
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
          return OpenAiResponseExtractor.extractText(resp.body());
        }

        // Handle 429/5xx with backoff
        if (sc == 429 || sc / 100 == 5) {
          long sleepMs = retryDelayMs(resp.headers(), attempt);
          if (attempt <= MAX_RETRIES) {
            Thread.sleep(sleepMs);
            continue;
          }
        }

        throw new IOException("OpenAI API error (" + sc + "): " + resp.body());

      } catch (InterruptedException ie) {
        // Preserve interrupt status and rethrow
        Thread.currentThread().interrupt();
        lastInt = ie;
        break;
      } catch (IOException ioe) {
        lastIo = ioe;
        // Retry on network errors up to MAX_RETRIES
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
    // Honor Retry-After if present
    return headers.firstValue("retry-after")
        .map(v -> {
          try { return Long.parseLong(v) * 1000L; } catch (NumberFormatException e) { return backoffMs(attempt); }
        })
        .orElseGet(() -> backoffMs(attempt));
  }

  private static long backoffMs(int attempt) {
    // Exponential backoff with jitter
    long base = (long) Math.min(30_000, Math.pow(2, attempt) * 500L);
    long jitter = (long) (Math.random() * 250);
    return base + jitter;
  }

}
