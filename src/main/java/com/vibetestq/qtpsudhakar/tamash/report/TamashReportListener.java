package com.vibetestq.qtpsudhakar.tamash.report;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.Tamash;
import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Auto-registered (via {@code META-INF/services}) JUnit Platform listener. It:
 * <ul>
 *   <li>attributes each heal to its test ({@link CurrentTest}) and clears the per-test heal cache /
 *       hint around every test — so a bare {@code SelfHealingDriver.wrap(...)} with no
 *       {@code @UseTamashSelenium} still gets per-test heal attribution and isolation;</li>
 *   <li>drives {@link TamashReport} when {@code TAMASH_REPORT} points at an output path — per-test
 *       timing/status, and the HTML render at session end.</li>
 * </ul>
 * When a framework integration is also active its {@code beforeEach} sets the same {@link CurrentTest}
 * values immediately after (idempotent).
 */
public final class TamashReportListener implements TestExecutionListener {

  private static String testId(TestIdentifier id) {
    return id.getSource()
        .filter(s -> s instanceof MethodSource)
        .map(s -> (MethodSource) s)
        .map(s -> s.getClassName() + "#" + s.getMethodName())
        .orElse(id.getDisplayName());
  }

  @Override
  public void testPlanExecutionStarted(TestPlan testPlan) {
    TamashReport.enableIfConfigured();
  }

  private static String methodName(TestIdentifier id) {
    return id.getSource()
        .filter(s -> s instanceof MethodSource)
        .map(s -> ((MethodSource) s).getMethodName())
        .orElse(null);
  }

  private static void endTest() {
    CurrentTest.clear();
    Tamash.clearHint();
    HealCache.clear();
    TamashReport.setCurrentTest(null);
  }

  @Override
  public void executionStarted(TestIdentifier id) {
    if (!id.isTest()) {
      return;
    }
    String tid = testId(id);
    id.getSource().filter(s -> s instanceof MethodSource).map(s -> (MethodSource) s).ifPresent(s ->
        CurrentTest.set(new CurrentTest.Info(s.getClassName(), methodName(id), id.getDisplayName())));
    TamashReport.setCurrentTest(tid);
    if (TamashReport.isEnabled()) {
      TamashReport.startTest(tid);
    }
  }

  @Override
  public void executionFinished(TestIdentifier id, TestExecutionResult result) {
    if (!id.isTest()) {
      return;
    }
    if (TamashReport.isEnabled()) {
      String status = switch (result.getStatus()) {
        case SUCCESSFUL -> "passed";
        case FAILED -> "failed";
        case ABORTED -> "skipped";
      };
      TamashReport.finishTest(testId(id), status);
    }
    endTest();
  }

  @Override
  public void executionSkipped(TestIdentifier id, String reason) {
    if (!id.isTest()) {
      return;
    }
    if (TamashReport.isEnabled()) {
      TamashReport.finishTest(testId(id), "skipped");
    }
    endTest();
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    TamashReport.finish();
  }
}
