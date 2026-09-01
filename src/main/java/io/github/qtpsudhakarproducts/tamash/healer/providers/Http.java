package io.github.qtpsudhakarproducts.tamash.healer.providers;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Shared HTTP POST for the raw-HTTP providers (OpenAI / Gemini / Ollama / Anthropic /
 *  claude-subscription). Returns the parsed JSON body, or null on any non-2xx / error, logging a
 *  {@code [self-healer]} warning — matching every provider's swallow-and-return-null contract. */
final class Http {
  private Http() {}

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  /** Retried once, with a short backoff, on the transient statuses (rate limit / overloaded /
   *  gateway) — a free-tier key mid-heal-batch or a briefly overloaded model shouldn't fail a run. */
  private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

  static JSONObject postJson(String label, String url, Map<String, String> headers, JSONObject body, double timeoutMs) {
    HttpRequest.Builder rb = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMillis((long) timeoutMs))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    headers.forEach(rb::header);
    HttpRequest request = rb.build();

    int attempts = 0;
    while (true) {
      attempts++;
      try {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
          if (RETRYABLE.contains(response.statusCode()) && attempts < 3) {
            sleep(response, attempts);
            continue;
          }
          System.out.println("[self-healer] " + label + " request failed: " + response.statusCode()
              + " " + firstLine(response.body()));
          return null;
        }
        return new JSONObject(response.body());
      } catch (java.net.http.HttpTimeoutException e) {
        if (attempts < 3) {
          sleep(null, attempts);
          continue;
        }
        System.out.println("[self-healer] " + label + " provider error: request timed out");
        return null;
      } catch (Exception e) {
        System.out.println("[self-healer] " + label + " provider error: " + e.getMessage());
        return null;
      }
    }
  }

  private static void sleep(HttpResponse<String> response, int attempt) {
    long ms = 500L * attempt;
    if (response != null) {
      ms = response.headers().firstValue("retry-after")
          .map(v -> { try { return Long.parseLong(v.trim()) * 1000L; } catch (Exception e) { return null; } })
          .filter(v -> v != null && v <= 15_000)
          .orElse(ms);
    }
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }

  private static String firstLine(String s) {
    if (s == null) return "";
    int nl = s.indexOf('\n');
    String line = nl == -1 ? s : s.substring(0, nl);
    return line.length() > 300 ? line.substring(0, 300) : line;
  }
}
