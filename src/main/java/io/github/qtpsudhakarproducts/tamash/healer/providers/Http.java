package io.github.qtpsudhakarproducts.tamash.healer.providers;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Shared HTTP POST for the raw-HTTP providers (OpenAI / Gemini / Ollama / Anthropic /
 *  claude-subscription). Returns the parsed JSON body, or null on any non-2xx / error, logging a
 *  {@code [self-healer]} warning — matching every provider's swallow-and-return-null contract. */
final class Http {
  private Http() {}

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  static JSONObject postJson(String label, String url, Map<String, String> headers, JSONObject body, double timeoutMs) {
    try {
      HttpRequest.Builder rb = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofMillis((long) timeoutMs))
          .header("content-type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
      headers.forEach(rb::header);

      HttpResponse<String> response = CLIENT.send(rb.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        System.out.println("[self-healer] " + label + " request failed: " + response.statusCode()
            + " " + firstLine(response.body()));
        return null;
      }
      return new JSONObject(response.body());
    } catch (Exception e) {
      System.out.println("[self-healer] " + label + " provider error: " + e.getMessage());
      return null;
    }
  }

  private static String firstLine(String s) {
    if (s == null) return "";
    int nl = s.indexOf('\n');
    String line = nl == -1 ? s : s.substring(0, nl);
    return line.length() > 300 ? line.substring(0, 300) : line;
  }
}
