package com.vibetestq.qtpsudhakar.tamash.report;

import com.vibetestq.qtpsudhakar.tamash.healer.providers.TokenUsage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A self-healing-aware step recorder + HTML report. Analogue of the Python port's {@code report}
 * module — off by default, enabled by pointing {@code TAMASH_REPORT} at an output path (env var,
 * {@code -DTAMASH_REPORT=...}, or {@code .env}). {@link com.vibetestq.qtpsudhakar.tamash.report.TamashReportListener}
 * (auto-registered JUnit Platform listener) turns it on at session start and renders it at
 * session end; the framework integration ({@code @UseTamashSelenium}, {@code TamashSeleniumTestNgTest},
 * or the Cucumber hooks) sets the current test, and the binding proxy records each step.
 *
 * <p>Steps are written to disk per test (under {@code target/.tamash-report/}) rather than held
 * in memory for the whole run, so a forked/parallel Surefire still aggregates correctly at the
 * end — same reasoning as the Python port's pytest-xdist handling.
 */
public final class TamashReport {
  private TamashReport() {}

  private static volatile boolean enabled = false;
  private static volatile Path outputPath;
  private static volatile Path storeDir;

  private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> Boolean.FALSE);

  private static final Map<String, List<Step>> STEPS_BY_TEST = new ConcurrentHashMap<>();
  private static final Map<String, Long> START_NANOS = new ConcurrentHashMap<>();
  // Tests that actually went through a tamash-selenium integration — so pure unit tests (which
  // never touch a driver) don't clutter the report with empty "No actions recorded" rows.
  private static final java.util.Set<String> MANAGED = ConcurrentHashMap.newKeySet();

  public static final class Step {
    public String category = "action"; // action | assert | fixture
    public String action;
    public String element;
    public String locator;
    public String value;
    public double durationMs;
    public boolean healed;
    public String error;
    public String suggestedSelector;
    public String provider;
    public String failureStage;
    public TokenUsage tokenUsage;
    public boolean usedActionRecovery;
    public Boolean needsReview;
    public String reviewNote;
    public String ariaSnapshot;

    JSONObject toJson() {
      JSONObject o = new JSONObject();
      o.put("category", category);
      o.put("action", action);
      o.put("element", element == null ? "" : element);
      o.put("locator", locator);
      o.put("value", value);
      o.put("duration_ms", durationMs);
      o.put("healed", healed);
      o.put("error", error);
      o.put("suggested_selector", suggestedSelector);
      o.put("provider", provider);
      o.put("failure_stage", failureStage);
      if (tokenUsage != null) {
        JSONObject tu = new JSONObject();
        tu.put("input_tokens", tokenUsage.getInputTokens());
        tu.put("output_tokens", tokenUsage.getOutputTokens());
        tu.put("total_tokens", tokenUsage.getTotalTokens());
        o.put("token_usage", tu);
      }
      o.put("used_action_recovery", usedActionRecovery);
      o.put("needs_review", needsReview);
      o.put("review_note", reviewNote);
      o.put("aria_snapshot", ariaSnapshot);
      return o;
    }
  }

  /** Try-with-resources guard that suppresses step recording — used around the healer's own
   *  inner replay call, which would otherwise record a duplicate of the outer failing action. */
  public static final class Suppress implements AutoCloseable {
    private final boolean prev;
    public Suppress() {
      prev = SUPPRESSED.get();
      SUPPRESSED.set(Boolean.TRUE);
    }
    @Override public void close() {
      SUPPRESSED.set(prev);
    }
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void enable(Path output, Path store) {
    enabled = true;
    outputPath = output;
    storeDir = store;
    try {
      // Fresh run — drop any per-test JSON left over from a previous one.
      if (Files.isDirectory(store)) {
        try (var s = Files.list(store)) {
          s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { /* best-effort */ }
          });
        }
      }
      Files.createDirectories(store);
    } catch (IOException ignored) {
      // best-effort
    }
  }

  /** Enables the report when {@code TAMASH_REPORT} points at an output path (env var,
   *  {@code -DTAMASH_REPORT=…}, or {@code .env}). Called by every framework integration's
   *  session-start hook; idempotent. */
  public static void enableIfConfigured() {
    if (enabled) {
      return;
    }
    String out = com.vibetestq.qtpsudhakar.tamash.Env.get("TAMASH_REPORT");
    if (out != null && !out.isBlank()) {
      enable(Path.of(out.trim()), Path.of("target", ".tamash-report"));
    }
  }

  public static void setCurrentTest(String testId) {
    if (testId == null) {
      CURRENT_TEST.remove();
    } else {
      CURRENT_TEST.set(testId);
      if (enabled) {
        MANAGED.add(testId);
      }
    }
  }

  public static String getCurrentTest() {
    return CURRENT_TEST.get();
  }

  public static void recordStep(Step step) {
    if (!enabled || SUPPRESSED.get()) {
      return;
    }
    String testId = CURRENT_TEST.get();
    if (testId == null) {
      return;
    }
    STEPS_BY_TEST.computeIfAbsent(testId, k -> new CopyOnWriteArrayList<>()).add(step);
  }

  public static void startTest(String testId) {
    if (!enabled) {
      return;
    }
    START_NANOS.put(testId, System.nanoTime());
  }

  /** Writes one test's accumulated steps + outcome to disk, then drops it from memory. */
  public static void finishTest(String testId, String status) {
    if (!enabled || storeDir == null) {
      return;
    }
    List<Step> steps = STEPS_BY_TEST.remove(testId);
    Long start = START_NANOS.remove(testId);
    // Only report tests that actually exercised the healing bindings.
    if (!MANAGED.remove(testId) && (steps == null || steps.isEmpty())) {
      return;
    }
    double durationMs = start != null ? (System.nanoTime() - start) / 1_000_000.0 : 0;

    JSONObject payload = new JSONObject();
    payload.put("nodeid", testId);
    payload.put("status", status);
    payload.put("duration_ms", durationMs);
    JSONArray arr = new JSONArray();
    if (steps != null) {
      for (Step s : steps) {
        arr.put(s.toJson());
      }
    }
    payload.put("steps", arr);
    try {
      Files.writeString(storeDir.resolve(sanitize(testId) + ".json"), payload.toString(), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      // best-effort
    }
  }

  /** Aggregates every per-test JSON and (re)renders the HTML report. Safe to call more than once
   *  per JVM — the per-test JSONs are left in place (cleared on the next {@link #enable}) so a run
   *  that ends multiple "sessions" (e.g. TestNG + Cucumber in one build) still renders the full
   *  set. */
  public static void finish() {
    if (!enabled || outputPath == null || storeDir == null) {
      return;
    }
    List<JSONObject> tests = new ArrayList<>();
    if (Files.isDirectory(storeDir)) {
      try (var stream = Files.list(storeDir)) {
        stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
          try {
            tests.add(new JSONObject(Files.readString(p, StandardCharsets.UTF_8)));
          } catch (Exception ignored) {
            // skip a corrupt file
          }
        });
      } catch (IOException ignored) {
        // best-effort
      }
    }
    if (tests.isEmpty()) {
      return;
    }
    try {
      Files.createDirectories(outputPath.toAbsolutePath().getParent() != null
          ? outputPath.toAbsolutePath().getParent() : Path.of("."));
      Files.writeString(outputPath, ReportRenderer.render(tests), StandardCharsets.UTF_8);
      System.out.println("[tamash] step report written to " + outputPath);
    } catch (IOException e) {
      System.out.println("[tamash] could not write step report: " + e.getMessage());
    }
  }

  private static String sanitize(String nodeId) {
    return nodeId.replaceAll("[^A-Za-z0-9_.#-]", "_");
  }
}
