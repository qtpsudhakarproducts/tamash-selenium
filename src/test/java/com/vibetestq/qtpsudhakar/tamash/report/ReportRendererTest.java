package com.vibetestq.qtpsudhakar.tamash.report;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Port of pw-java's ReportRendererTest: a synthetic report -> HTML, asserting the shape. */
class ReportRendererTest {

  private static JSONObject step(String action, String element, boolean healed, String suggested,
                                 Integer totalTokens, String error) {
    JSONObject s = new JSONObject();
    s.put("category", "action");
    s.put("action", action);
    s.put("element", element);
    s.put("duration_ms", 42.0);
    s.put("healed", healed);
    if (suggested != null) s.put("suggested_selector", suggested);
    if (healed) s.put("provider", "tamash");
    if (totalTokens != null) {
      s.put("token_usage", new JSONObject().put("total_tokens", totalTokens)
          .put("input_tokens", totalTokens - 10).put("output_tokens", 10));
    }
    if (error != null) s.put("error", error);
    return s;
  }

  private static JSONObject test(String nodeId, String status, JSONObject... steps) {
    JSONArray arr = new JSONArray();
    for (JSONObject s : steps) arr.put(s);
    return new JSONObject().put("nodeid", nodeId).put("status", status)
        .put("duration_ms", 1234.0).put("steps", arr);
  }

  @Test
  void rendersSummaryStepsAndHealNote() {
    String html = ReportRenderer.render(List.of(
        test("com.foo.LoginTest#logsIn", "passed",
            step("sendKeys", "Username Textbox", true, "By.name(\"username\")", 180, null),
            step("click", "Login Button", false, null, null, null)),
        test("com.foo.LoginTest#failsCleanly", "failed",
            step("sendKeys", "Missing Field", false, null, null,
                "Action \"sendKeys\" failed: NoSuchElementException"))));

    assertTrue(html.startsWith("<!DOCTYPE html>"), html.substring(0, 40));
    assertTrue(html.contains("<title>tamash-selenium report</title>"), "title");
    // summary stats
    assertTrue(html.contains(">2</span><span class=\"stat-label\">tests</span>"), "tests=2");
    assertTrue(html.contains(">1</span><span class=\"stat-label\">passed</span>"), "passed=1");
    assertTrue(html.contains(">1</span><span class=\"stat-label\">failed</span>"), "failed=1");
    assertTrue(html.contains(">1</span><span class=\"stat-label\">healed steps</span>"), "healed=1");
    assertTrue(html.contains("tokens used"), "token stat");
    // per-test
    assertTrue(html.contains("com.foo.LoginTest#logsIn"), "test id");
    assertTrue(html.contains("class=\"badge failed\""), "failed badge");
    assertTrue(html.contains("1 healed"), "healed chip");
    // step + heal note
    assertTrue(html.contains(">sendKeys</span>"), "action label");
    assertTrue(html.contains("healed via tamash"), "heal note");
    assertTrue(html.contains("recovered as By.name(&quot;username&quot;)"), "recovered selector");
    assertTrue(html.contains("180"), "token count");
    // failed step shows the error
    assertTrue(html.contains("NoSuchElementException"), "error text");
    // charts present
    assertTrue(html.contains("Duration by test") || html.contains("duration"), "duration chart");
  }

  @Test
  void escapesHtmlInStepText() {
    String html = ReportRenderer.render(List.of(
        test("t#x", "passed", step("sendKeys", "<script>alert(1)</script>", false, null, null, null))));
    assertFalse(html.contains("<script>alert(1)</script>"), "raw script must not survive");
    assertTrue(html.contains("&lt;script&gt;"), "escaped");
  }

  @Test
  void emptyStepsRendersPlaceholder() {
    String html = ReportRenderer.render(List.of(test("t#empty", "passed")));
    assertTrue(html.contains("No actions recorded."), "placeholder");
  }

  @Test
  void unrecoveredFailureRendersDomSnapshot() {
    JSONObject s = step("click", "Save Button", false, null, null, "NoSuchElementException");
    s.put("aria_snapshot", "- generic [ref=e1]:\n  - button \"Save\" [ref=e2]");
    String html = ReportRenderer.render(List.of(test("t#fail", "failed", s)));
    assertTrue(html.contains("DOM snapshot at failure"), "snapshot details block");
    assertTrue(html.contains("button &quot;Save&quot; [ref=e2]"), "snapshot body, escaped");
  }

  @Test
  void noHealingCallsRendersChartPlaceholder() {
    String html = ReportRenderer.render(List.of(
        test("t#plain", "passed", step("click", "OK", false, null, null, null))));
    assertTrue(html.contains("No healing calls made."), "token chart placeholder");
  }
}
