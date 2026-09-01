package com.vibetestq.qtpsudhakar.tamash.testng;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.TamashHeals;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.report.TamashReport;
import org.testng.IExecutionListener;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.Reporter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-registered (via {@code META-INF/services/org.testng.ITestNGListener}) TestNG listener — the
 * TestNG counterpart of the JUnit {@code TamashReportListener} + the {@code TestWatcher} half of
 * {@code @UseTamashSelenium}. Enables the HTML step report, tracks per-test id/timing/status,
 * attaches each test's self-heal reports ({@code Reporter.log} + a JSON file under
 * {@code target/tamash-heals/}), and renders the HTML at the end of the run.
 *
 * <p>The driver lifecycle is provided by {@link TamashSeleniumTestNgTest} (a base class) — TestNG
 * has no parameter injection for arbitrary types.
 */
public final class TamashSeleniumTestNgListener implements IExecutionListener, ITestListener {

  private static final Path HEALS_DIR = Path.of("target", "tamash-heals");

  @Override
  public void onExecutionStart() {
    TamashReport.enableIfConfigured();
  }

  @Override
  public void onExecutionFinish() {
    TamashReport.finish();
  }

  @Override
  public void onTestStart(ITestResult result) {
    String id = idOf(result);
    CurrentTest.set(new CurrentTest.Info(result.getTestClass().getName(), result.getMethod().getMethodName(), id));
    TamashReport.setCurrentTest(id);
    TamashReport.startTest(id);
  }

  @Override public void onTestSuccess(ITestResult result) { finish(result, "passed"); }
  @Override public void onTestFailure(ITestResult result) { finish(result, "failed"); }
  @Override public void onTestSkipped(ITestResult result) { finish(result, "skipped"); }

  private void finish(ITestResult result, String status) {
    String id = idOf(result);
    var reports = TamashHeals.forTest(id);
    if (!reports.isEmpty()) {
      Reporter.log("[tamash] " + TamashHeals.summary(reports) + " — "
          + reports.stream().map(SelfHealingReport::getWarning).findFirst().orElse(""), true);
      try {
        Files.createDirectories(HEALS_DIR);
        Files.writeString(HEALS_DIR.resolve(sanitize(id) + ".json"), TamashHeals.toJson(reports), StandardCharsets.UTF_8);
        for (SelfHealingReport r : reports) {
          if (!r.isHealed() && r.ariaSnapshotForReport != null) {
            Files.writeString(HEALS_DIR.resolve(sanitize(id) + "-dom-" + r.getAction() + ".txt"),
                r.ariaSnapshotForReport, StandardCharsets.UTF_8);
          }
        }
      } catch (Exception ignored) {
        // best-effort attachment
      }
    }
    TamashReport.finishTest(id, status);
    CurrentTest.clear();
    TamashReport.setCurrentTest(null);
  }

  private static String idOf(ITestResult result) {
    return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
  }

  private static String sanitize(String s) {
    return s.replaceAll("[^A-Za-z0-9_.#-]", "_");
  }
}
