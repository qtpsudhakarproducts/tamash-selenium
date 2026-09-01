package com.vibetestq.qtpsudhakar.tamash.healer;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Selenium stand-in for Playwright's {@code ariaSnapshot(mode:AI)}. A single injected script
 * ({@code /com/vibetestq/qtpsudhakar/tamash/healer/dom-snapshot.js}) walks the live DOM and
 * emits a YAML accessibility tree in the exact shape {@link DurableLocator#parseAriaAiTree}
 * parses:
 *
 * <pre>
 *   - generic [ref=e1] [box=8,21,1264,139]:
 *     - heading "Sign in" [ref=e2] [box=8,21,300,37]
 *     - textbox "Username" [ref=e3] [box=8,80,177,21]
 *     - text: Forgot password?
 * </pre>
 *
 * <p>Every emitted element is stamped {@code data-tamash-ref="eN"} so an AI-picked {@code [ref=eN]}
 * resolves to a real node via {@code By.cssSelector("[data-tamash-ref='eN']")} (the same tagging
 * trick vision-based tools use). {@link #clearRefs} removes them again after a heal (best-effort).
 */
public final class DomSnapshot {
  private DomSnapshot() {}

  private static final String CAPTURE_JS = loadScript();
  private static final String CLEAR_JS =
      "document.querySelectorAll('[data-tamash-ref]').forEach(function(e){e.removeAttribute('data-tamash-ref');});";

  private static String loadScript() {
    try (InputStream in = DomSnapshot.class.getResourceAsStream("dom-snapshot.js")) {
      if (in == null) {
        return null;
      }
      // The file is an IIFE; executeScript needs a top-level `return` to see its value.
      return "return " + new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  /** Captures the accessibility tree for {@code driver}'s current document / active frame.
   *  Returns null if the script fails (falls through to {@code failureStage=no_snapshot}). */
  public static String capture(WebDriver driver) {
    if (CAPTURE_JS == null || driver == null) {
      return null;
    }
    try {
      Object result = ((JavascriptExecutor) driver).executeScript(CAPTURE_JS);
      String yaml = result != null ? result.toString() : null;
      if (System.getenv("TAMASH_DEBUG") != null || System.getProperty("TAMASH_DEBUG") != null) {
        System.err.println("[tamash-debug] snapshot len=" + (yaml == null ? "null" : yaml.length()));
      }
      return (yaml == null || yaml.isBlank()) ? null : yaml;
    } catch (Exception e) {
      if (System.getenv("TAMASH_DEBUG") != null || System.getProperty("TAMASH_DEBUG") != null) {
        System.err.println("[tamash-debug] snapshot failed: " + e);
      }
      return null;
    }
  }

  /** Best-effort — the healed action may already have navigated away or replaced the nodes. */
  public static void clearRefs(SearchContext ctx) {
    try {
      WebDriver driver = driverOf(ctx);
      if (driver != null) {
        ((JavascriptExecutor) driver).executeScript(CLEAR_JS);
      }
    } catch (Exception ignored) {
      // best-effort
    }
  }

  static WebDriver driverOf(SearchContext ctx) {
    if (ctx instanceof WebDriver d) {
      return d;
    }
    if (ctx instanceof WrapsDriver w) {
      return w.getWrappedDriver();
    }
    return null;
  }
}
