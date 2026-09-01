package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@code Http.postJson}'s retry policy (added in 0.1.0-beta.3): retry once, and only when
 * the server named a short delay — a bare 429 is a per-minute quota and must fail fast.
 */
class HttpRetryTest {

  private HttpServer server;
  private final AtomicInteger hits = new AtomicInteger();

  /** Spin a local server whose handler is `(exchange-attempt-number) -> writes a response`. */
  private String start(BiConsumer<com.sun.net.httpserver.HttpExchange, Integer> handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> {
      int n = hits.incrementAndGet();
      ex.getRequestBody().readAllBytes();
      handler.accept(ex, n);
      ex.close();
    });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  private static void send(com.sun.net.httpserver.HttpExchange ex, int status, String body) {
    try {
      byte[] b = body.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(status, b.length);
      try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private JSONObject post(String url) {
    return Http.postJson("test", url, Map.of(), new JSONObject().put("q", 1), 5000);
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void success_noRetry() throws IOException {
    String url = start((ex, n) -> send(ex, 200, "{\"ok\":true}"));
    assertTrue(post(url).getBoolean("ok"));
    assertEquals(1, hits.get());
  }

  @Test
  void retryAfterHeader_shortDelay_retriesOnce() throws IOException {
    String url = start((ex, n) -> {
      if (n == 1) { ex.getResponseHeaders().add("Retry-After", "1"); send(ex, 429, "slow down"); }
      else send(ex, 200, "{\"ok\":true}");
    });
    assertTrue(post(url).getBoolean("ok"));
    assertEquals(2, hits.get());
  }

  @Test
  void retryAfterHeader_longDelay_failsFast() throws IOException {
    String url = start((ex, n) -> { ex.getResponseHeaders().add("Retry-After", "60"); send(ex, 429, "quota"); });
    assertNull(post(url));
    assertEquals(1, hits.get(), "a per-minute quota must not be retried");
  }

  @Test
  void geminiRetryDelayBodyField_retriesOnce() throws IOException {
    String url = start((ex, n) -> {
      if (n == 1) send(ex, 429, "{\"error\":{\"details\":[{\"retryDelay\":\"1s\"}]}}");
      else send(ex, 200, "{\"ok\":true}");
    });
    assertTrue(post(url).getBoolean("ok"));
    assertEquals(2, hits.get());
  }

  @Test
  void bare429_noDelayHint_failsFast() throws IOException {
    String url = start((ex, n) -> send(ex, 429, "Too Many Requests"));
    assertNull(post(url));
    assertEquals(1, hits.get());
  }

  @Test
  void serverError_retriesOnceThenGivesUp() throws IOException {
    String url = start((ex, n) -> send(ex, 503, "unavailable"));
    assertNull(post(url));
    assertEquals(2, hits.get(), "5xx gets exactly one quick retry");
  }

  @Test
  void serverError_thenSuccess() throws IOException {
    String url = start((ex, n) -> {
      if (n == 1) send(ex, 500, "boom");
      else send(ex, 200, "{\"ok\":true}");
    });
    assertTrue(post(url).getBoolean("ok"));
    assertEquals(2, hits.get());
  }
}
