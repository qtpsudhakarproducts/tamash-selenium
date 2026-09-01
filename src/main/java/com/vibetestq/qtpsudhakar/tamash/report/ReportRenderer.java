package com.vibetestq.qtpsudhakar.tamash.report;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Port of the Python port's {@code report/render.py} — a self-contained HTML step report. */
final class ReportRenderer {
  private ReportRenderer() {}

  private static final String CSS = """
      :root {
        --bg: #f5f6f8;
        --surface: #ffffff;
        --border: #e1e4ea;
        --text: #1a2233;
        --text-muted: #6b7385;
        --accent: #3d5a80;
        --pass: #2f9e6e;
        --pass-bg: #e7f6ef;
        --fail: #d64545;
        --fail-bg: #fbeaea;
        --healed: #c9820c;
        --healed-bg: #fbf1de;
        --skip: #8b93a3;
        --skip-bg: #eef0f3;
        --cat-action: #5b6b85;
        --cat-assert: #7c3aed;
        --cat-fixture: #0f766e;
        --step-indent: calc(8px + 0.6rem + 140px + 0.6rem + 56px + 0.6rem);
      }
      * { box-sizing: border-box; }
      body { margin: 0; padding: 2.5rem 1.5rem 4rem; background: var(--bg); color: var(--text);
        font-family: ui-sans-serif, -apple-system, "Segoe UI", Roboto, sans-serif; font-size: 14px; line-height: 1.5; }
      main { max-width: 920px; margin: 0 auto; display: flex; flex-direction: column; gap: 0.75rem; }
      header.summary { max-width: 920px; margin: 0 auto 2rem; }
      h1 { font-size: 1.5rem; font-weight: 650; letter-spacing: -0.01em; margin: 0 0 1.1rem; }
      .stat-row { display: flex; flex-wrap: wrap; gap: 0.6rem; }
      .stat { background: var(--surface); border: 1px solid var(--border); border-radius: 10px;
        padding: 0.6rem 1rem; min-width: 88px; text-align: center; }
      .stat-value { display: block; font-family: ui-monospace, Consolas, monospace; font-variant-numeric: tabular-nums;
        font-size: 1.25rem; font-weight: 600; }
      .stat-label { display: block; font-size: 0.7rem; color: var(--text-muted); text-transform: uppercase;
        letter-spacing: 0.05em; margin-top: 0.15rem; }
      .stat.pass .stat-value { color: var(--pass); }
      .stat.fail .stat-value { color: var(--fail); }
      .stat.healed .stat-value { color: var(--healed); }
      .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; max-width: 920px; margin: 0 auto 1.5rem; }
      @media (max-width: 640px) { .charts { grid-template-columns: 1fr; } }
      .chart { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 1rem 1.1rem 1.1rem; }
      .chart h2 { margin: 0 0 0.7rem; font-size: 0.72rem; font-weight: 700; text-transform: uppercase;
        letter-spacing: 0.05em; color: var(--text-muted); }
      .chart-row { display: flex; align-items: center; gap: 0.6rem; padding: 0.28rem 0; }
      .chart-label { flex-shrink: 0; width: 40%; font-size: 0.78rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .chart-track { flex: 1; height: 10px; background: var(--bg); border-radius: 5px; overflow: hidden; }
      .chart-bar { height: 100%; border-radius: 5px; background: var(--accent); }
      .chart-row.failed .chart-bar { background: var(--fail); }
      .chart-value { flex-shrink: 0; min-width: 64px; text-align: right; font-family: ui-monospace, Consolas, monospace;
        font-variant-numeric: tabular-nums; font-size: 0.76rem; color: var(--text-muted); }
      .chart-empty { font-size: 0.8rem; color: var(--text-muted); }
      .test { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; overflow: hidden; }
      .test-header { display: flex; align-items: center; gap: 0.75rem; padding: 0.85rem 1.1rem; cursor: pointer; user-select: none; }
      .badge { flex-shrink: 0; font-size: 0.65rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase;
        padding: 0.2rem 0.5rem; border-radius: 5px; }
      .badge.passed { color: var(--pass); background: var(--pass-bg); }
      .badge.failed { color: var(--fail); background: var(--fail-bg); }
      .badge.skipped { color: var(--skip); background: var(--skip-bg); }
      .test-title { flex: 1; font-weight: 550; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .test-meta { flex-shrink: 0; font-family: ui-monospace, Consolas, monospace; font-variant-numeric: tabular-nums;
        color: var(--text-muted); font-size: 0.8rem; }
      .healed-chip { flex-shrink: 0; font-size: 0.7rem; color: var(--healed); background: var(--healed-bg);
        padding: 0.15rem 0.45rem; border-radius: 5px; font-weight: 600; }
      .chevron { flex-shrink: 0; color: var(--text-muted); transition: transform 0.15s ease; }
      .test.open .chevron { transform: rotate(90deg); }
      .steps { display: none; border-top: 1px solid var(--border); padding: 0.6rem 1.1rem 0.9rem; }
      .test.open .steps { display: block; }
      .step-group { display: block; }
      .step-group[hidden] { display: none; }
      .step { display: flex; align-items: center; gap: 0.6rem; padding: 0.3rem 0; }
      .step-category { flex-shrink: 0; width: 8px; height: 8px; border-radius: 50%; background: var(--cat-action); }
      .step-bar-track { flex-shrink: 0; width: 140px; height: 8px; background: var(--bg); border-radius: 4px; overflow: hidden; }
      .step-bar { height: 100%; border-radius: 4px; background: var(--text-muted); }
      .step.healed .step-bar { background: var(--healed); }
      .step.failed .step-bar { background: var(--fail); }
      .step-title { flex: 1; font-family: ui-monospace, Consolas, monospace; font-size: 0.82rem; overflow: hidden;
        text-overflow: ellipsis; white-space: nowrap; }
      .step.healed .step-title { color: var(--healed); }
      .step.failed .step-title { color: var(--fail); }
      .step-action { flex-shrink: 0; font-family: ui-monospace, Consolas, monospace; font-size: 0.66rem; font-weight: 700;
        text-transform: uppercase; letter-spacing: 0.03em; color: var(--text-muted); border: 1px solid var(--border);
        border-radius: 4px; padding: 0.08rem 0.4rem; min-width: 56px; text-align: center; }
      .step.healed .step-action { color: var(--healed); border-color: var(--healed); }
      .step.failed .step-action { color: var(--fail); border-color: var(--fail); }
      .step-locator, .step-value { padding-left: var(--step-indent); font-family: ui-monospace, Consolas, monospace;
        font-size: 0.76rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: -0.05rem 0 0.3rem; }
      .step-locator { color: var(--text-muted); }
      .step-value { color: var(--accent); }
      .step-value::before { content: "\\2192  "; color: var(--text-muted); }
      .step-duration { flex-shrink: 0; font-family: ui-monospace, Consolas, monospace; font-variant-numeric: tabular-nums;
        font-size: 0.78rem; color: var(--text-muted); min-width: 48px; text-align: right; }
      .step-note { margin: 0.15rem 0 0.35rem; padding-left: var(--step-indent); font-size: 0.78rem; color: var(--text-muted); }
      .step-note.healed-note { color: var(--healed); }
      .step-screenshot { padding-left: var(--step-indent); margin: 0.2rem 0 0.4rem; }
      .step-screenshot img { max-width: 220px; max-height: 140px; border: 1px solid var(--border); border-radius: 6px; cursor: zoom-in; }
      .step-aria-snapshot { padding-left: var(--step-indent); margin: 0.2rem 0 0.4rem; font-size: 0.78rem; }
      .step-aria-snapshot summary { color: var(--text-muted); cursor: pointer; }
      .step-aria-snapshot pre { max-height: 280px; overflow: auto; margin: 0.3rem 0 0; padding: 0.5rem 0.6rem;
        border: 1px solid var(--border); border-radius: 6px; background: var(--surface); white-space: pre-wrap; word-break: break-word; }
      .empty { text-align: center; color: var(--text-muted); padding: 3rem 0; }
      """;

  private static final String JS = """
      document.querySelectorAll('.test-header').forEach(function (el) {
        el.addEventListener('click', function () { el.parentElement.classList.toggle('open'); });
      });
      """;

  private static String esc(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value)
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  private static Integer i(JSONObject o, String key) {
    return o.has(key) && !o.isNull(key) ? o.optInt(key) : null;
  }

  private static long effectiveTokens(JSONObject usage) {
    if (usage == null) {
      return 0;
    }
    Integer total = i(usage, "total_tokens");
    if (total != null && total != 0) {
      return total;
    }
    Integer in = i(usage, "input_tokens");
    Integer out = i(usage, "output_tokens");
    return (in == null ? 0 : in) + (out == null ? 0 : out);
  }

  private static String formatTokens(JSONObject usage) {
    if (usage == null) {
      return "";
    }
    Integer total = i(usage, "total_tokens");
    Integer in = i(usage, "input_tokens");
    Integer out = i(usage, "output_tokens");
    if (total == null && in == null && out == null) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    if (in != null) parts.add(in + " in");
    if (out != null) parts.add(out + " out");
    String breakdown = parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    String totalStr = total != null ? total + " tokens" : "tokens";
    return " &mdash; " + esc(totalStr) + esc(breakdown);
  }

  private static String shortName(String nodeId) {
    int idx = nodeId.lastIndexOf('#');
    return idx == -1 ? nodeId : nodeId.substring(idx + 1);
  }

  private static long sumTokens(List<JSONObject> tests) {
    long total = 0;
    for (JSONObject t : tests) {
      for (Object s : t.optJSONArray("steps")) {
        total += effectiveTokens(((JSONObject) s).optJSONObject("token_usage"));
      }
    }
    return total;
  }

  private static String renderBarChart(List<Object[]> rows, boolean seconds) {
    if (rows.isEmpty()) {
      return "<p class=\"chart-empty\">No data.</p>";
    }
    double max = rows.stream().mapToDouble(r -> (double) r[1]).max().orElse(1.0);
    if (max == 0) max = 1.0;
    StringBuilder sb = new StringBuilder();
    for (Object[] r : rows) {
      String label = (String) r[0];
      double value = (double) r[1];
      boolean failed = (boolean) r[2];
      double pct = Math.max(2.0, (value / max) * 100);
      String valueStr = seconds ? String.format("%.1fs", value / 1000) : String.format("%,d", (long) value);
      sb.append("<div class=\"chart-row ").append(failed ? "failed" : "").append("\">")
          .append("<div class=\"chart-label\">").append(esc(label)).append("</div>")
          .append("<div class=\"chart-track\"><div class=\"chart-bar\" style=\"width:")
          .append(String.format("%.0f", pct)).append("%\"></div></div>")
          .append("<div class=\"chart-value\">").append(esc(valueStr)).append("</div></div>\n");
    }
    return sb.toString();
  }

  private static String renderStep(JSONObject step, double maxDurationMs) {
    boolean healed = step.optBoolean("healed", false);
    String error = step.isNull("error") ? null : step.optString("error", null);
    String cls = healed ? "healed" : (error != null ? "failed" : "");
    double duration = step.optDouble("duration_ms", 0);
    double pct = maxDurationMs <= 0 ? 0 : Math.max(4.0, (duration / maxDurationMs) * 100);
    JSONObject usage = step.optJSONObject("token_usage");

    String note = "";
    String suggested = step.isNull("suggested_selector") ? null : step.optString("suggested_selector", null);
    if (healed && suggested != null) {
      String reviewFlag = step.optBoolean("needs_review", false) ? " &mdash; <strong>needs review</strong>" : "";
      String provider = step.isNull("provider") ? "?" : step.optString("provider", "?");
      note = "<div class=\"step-note healed-note\">healed via " + esc(provider)
          + " &mdash; recovered as " + esc(suggested) + formatTokens(usage) + reviewFlag + "</div>";
      String reviewNote = step.isNull("review_note") ? null : step.optString("review_note", null);
      if (step.optBoolean("needs_review", false) && reviewNote != null) {
        note += "<div class=\"step-note\">" + esc(reviewNote) + "</div>";
      }
    } else if (error != null) {
      note = "<div class=\"step-note\">" + esc(error) + formatTokens(usage) + "</div>";
    }

    String locator = step.isNull("locator") ? null : step.optString("locator", null);
    String locatorLine = locator != null ? "<div class=\"step-locator\">" + esc(locator) + "</div>" : "";
    String value = step.isNull("value") ? null : step.optString("value", null);
    String valueLine = value != null ? "<div class=\"step-value\">" + esc(value) + "</div>" : "";

    String screenshotLine = "";
    String screenshot = step.isNull("screenshot") ? null : step.optString("screenshot", null);
    if (screenshot != null && !screenshot.isEmpty()) {
      String src = "data:image/png;base64," + screenshot;
      screenshotLine = "<div class=\"step-screenshot\"><a href=\"" + src + "\" target=\"_blank\" rel=\"noopener\">"
          + "<img src=\"" + src + "\" alt=\"Screenshot at failure\" /></a></div>";
    }

    String ariaLine = "";
    String aria = step.isNull("aria_snapshot") ? null : step.optString("aria_snapshot", null);
    if (aria != null && !aria.isEmpty()) {
      ariaLine = "<details class=\"step-aria-snapshot\"><summary>DOM snapshot at failure</summary>"
          + "<pre>" + esc(aria) + "</pre></details>";
    }

    return "<div class=\"step-group\" data-category=\"" + esc(step.optString("category", "action")) + "\">"
        + "<div class=\"step " + cls + "\">"
        + "<span class=\"step-category\"></span>"
        + "<div class=\"step-bar-track\"><div class=\"step-bar\" style=\"width:" + String.format("%.0f", pct) + "%\"></div></div>"
        + "<span class=\"step-action\">" + esc(step.optString("action", "")) + "</span>"
        + "<div class=\"step-title\">" + esc(step.optString("element", "")) + "</div>"
        + "<div class=\"step-duration\">" + String.format("%.0f", duration) + "ms</div>"
        + "</div>" + locatorLine + valueLine + screenshotLine + note + ariaLine
        + "</div>";
  }

  private static String renderTest(JSONObject test) {
    JSONArray steps = test.optJSONArray("steps");
    double maxDuration = 0;
    long healedCount = 0;
    for (Object s : steps) {
      JSONObject step = (JSONObject) s;
      maxDuration = Math.max(maxDuration, step.optDouble("duration_ms", 0));
      if (step.optBoolean("healed", false)) healedCount++;
    }
    StringBuilder stepsHtml = new StringBuilder();
    for (Object s : steps) {
      stepsHtml.append(renderStep((JSONObject) s, maxDuration)).append('\n');
    }
    if (steps.isEmpty()) {
      stepsHtml.append("<div class=\"step-note\">No actions recorded.</div>");
    }
    String healedChip = healedCount > 0 ? "<span class=\"healed-chip\">" + healedCount + " healed</span>" : "";
    String status = test.optString("status", "passed");

    return "<div class=\"test\">"
        + "<div class=\"test-header\">"
        + "<span class=\"badge " + esc(status) + "\">" + esc(status) + "</span>"
        + "<span class=\"test-title\">" + esc(test.optString("nodeid", "")) + "</span>"
        + healedChip
        + "<div class=\"test-meta-row\"><span class=\"test-meta\">"
        + String.format("%.0f", test.optDouble("duration_ms", 0)) + "ms</span></div>"
        + "<span class=\"chevron\">&#9656;</span>"
        + "</div>"
        + "<div class=\"steps\">" + stepsHtml + "</div>"
        + "</div>";
  }

  static String render(List<JSONObject> tests) {
    int total = tests.size();
    long passed = tests.stream().filter(t -> "passed".equals(t.optString("status"))).count();
    long failed = tests.stream().filter(t -> "failed".equals(t.optString("status"))).count();
    long skipped = tests.stream().filter(t -> "skipped".equals(t.optString("status"))).count();
    double totalDurationMs = tests.stream().mapToDouble(t -> t.optDouble("duration_ms", 0)).sum();
    long healedSteps = 0;
    for (JSONObject t : tests) {
      for (Object s : t.optJSONArray("steps")) {
        if (((JSONObject) s).optBoolean("healed", false)) healedSteps++;
      }
    }
    long totalTokens = sumTokens(tests);
    String tokensStat = totalTokens > 0
        ? "<div class=\"stat\"><span class=\"stat-value\">" + String.format("%,d", totalTokens)
          + "</span><span class=\"stat-label\">tokens used</span></div>"
        : "";

    StringBuilder body = new StringBuilder();
    for (JSONObject t : tests) {
      body.append(renderTest(t)).append('\n');
    }
    if (tests.isEmpty()) {
      body.append("<p class=\"empty\">No tests recorded.</p>");
    }

    List<Object[]> durationRows = new ArrayList<>();
    for (JSONObject t : tests) {
      durationRows.add(new Object[]{shortName(t.optString("nodeid")), t.optDouble("duration_ms", 0),
          "failed".equals(t.optString("status"))});
    }
    List<Object[]> tokenRows = new ArrayList<>();
    for (JSONObject t : tests) {
      long tk = 0;
      for (Object s : t.optJSONArray("steps")) {
        tk += effectiveTokens(((JSONObject) s).optJSONObject("token_usage"));
      }
      if (tk > 0) {
        tokenRows.add(new Object[]{shortName(t.optString("nodeid")), (double) tk, false});
      }
    }
    String tokensChart = tokenRows.isEmpty()
        ? "<p class=\"chart-empty\">No healing calls made.</p>"
        : renderBarChart(tokenRows, false);

    return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\" />\n"
        + "<title>tamash-selenium report</title>\n<style>" + CSS + "</style>\n</head>\n<body>\n"
        + "  <header class=\"summary\">\n    <h1>tamash-selenium report</h1>\n    <div class=\"stat-row\">\n"
        + "      <div class=\"stat\"><span class=\"stat-value\">" + total + "</span><span class=\"stat-label\">tests</span></div>\n"
        + "      <div class=\"stat pass\"><span class=\"stat-value\">" + passed + "</span><span class=\"stat-label\">passed</span></div>\n"
        + "      <div class=\"stat fail\"><span class=\"stat-value\">" + failed + "</span><span class=\"stat-label\">failed</span></div>\n"
        + "      <div class=\"stat\"><span class=\"stat-value\">" + skipped + "</span><span class=\"stat-label\">skipped</span></div>\n"
        + "      <div class=\"stat healed\"><span class=\"stat-value\">" + healedSteps + "</span><span class=\"stat-label\">healed steps</span></div>\n"
        + "      " + tokensStat + "\n"
        + "      <div class=\"stat\"><span class=\"stat-value\">" + String.format("%.1fs", totalDurationMs / 1000)
        + "</span><span class=\"stat-label\">duration</span></div>\n"
        + "    </div>\n  </header>\n"
        + "  <div class=\"charts\">\n    <div class=\"chart\">\n      <h2>Duration by test</h2>\n      "
        + renderBarChart(durationRows, true) + "\n    </div>\n"
        + "    <div class=\"chart\">\n      <h2>Tokens by test</h2>\n      " + tokensChart + "\n    </div>\n  </div>\n"
        + "  <main>\n    " + body + "\n  </main>\n"
        + "  <script>" + JS + "</script>\n</body>\n</html>\n";
  }
}
