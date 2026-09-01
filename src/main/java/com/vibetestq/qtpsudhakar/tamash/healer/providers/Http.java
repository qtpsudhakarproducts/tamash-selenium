package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared HTTP POST for the raw-HTTP providers (OpenAI / Gemini / Ollama / Anthropic /
 *  claude-subscription). Returns the parsed JSON body, or null on any non-2xx / error, logging a
 *  {@code [self-healer]} warning — matching every provider's swallow-and-return-null contract. */
final class Http {
  private Http() {}

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  /** A rate-limited call is only worth waiting on if the server tells us the window is short —
   *  otherwise it's a per-minute quota and retrying would just burn the healer's timeout. */
  private static final long MAX_RETRY_WAIT_MS = 8_000;

  private static final Pattern RETRY_DELAY_SECONDS = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"");

  static JSONObject postJson(String label, String url, Map<String, String> headers, JSONObject body, double timeoutMs) {
    HttpRequest.Builder rb = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMillis((long) timeoutMs))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    headers.forEach(rb::header);
    HttpRequest request = rb.build();

    boolean retried = false;
    while (true) {
      try {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 300) {
          return new JSONObject(response.body());
        }
        // 5xx: one quick retry (a brief overload). 429: retry once only if the server named a
        // short delay; a bare 429 is a per-minute quota — fail fast rather than stall the heal.
        if (!retried) {
          Long waitMs = null;
          if (status >= 500 && status <= 504) {
            waitMs = 1_000L;
          } else if (status == 429) {
            waitMs = retryAfterMs(response);
          }
          if (waitMs != null && waitMs <= MAX_RETRY_WAIT_MS) {
            retried = true;
            sleep(waitMs);
            continue;
          }
        }
        System.out.println("[self-healer] " + label + " request failed: " + status + " " + firstLine(response.body()));
        return null;
      } catch (java.net.http.HttpTimeoutException e) {
        if (!retried) {
          retried = true;
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

  /** Milliseconds to wait per the {@code Retry-After} header or Gemini's {@code retryDelay} body
   *  field, or null when the server gave no hint. */
  private static Long retryAfterMs(HttpResponse<String> response) {
    Long header = response.headers().firstValue("retry-after")
        .map(v -> { try { return Long.parseLong(v.trim()) * 1000L; } catch (Exception e) { return null; } })
        .orElse(null);
    if (header != null) {
      return header;
    }
    if (response.body() != null) {
      Matcher m = RETRY_DELAY_SECONDS.matcher(response.body());
      if (m.find()) {
        return Long.parseLong(m.group(1)) * 1000L;
      }
    }
    return null;
  }

  private static void sleep(long ms) {
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
