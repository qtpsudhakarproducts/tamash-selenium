package io.github.qtpsudhakarproducts.tamash.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Auto-registered (via {@code META-INF/services}) JUnit Platform listener that drives
 * {@link TamashReport}: enables it at session start when {@code TAMASH_REPORT} points at an output
 * path, tracks per-test timing/status, and renders the HTML at session end. The Java analogue of
 * the Python port's {@code pytest_configure} / {@code pytest_runtest_*} / {@code
 * pytest_sessionfinish} hooks.
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

  @Override
  public void executionStarted(TestIdentifier id) {
    if (id.isTest() && TamashReport.isEnabled()) {
      TamashReport.startTest(testId(id));
    }
  }

  @Override
  public void executionFinished(TestIdentifier id, TestExecutionResult result) {
    if (!id.isTest() || !TamashReport.isEnabled()) {
      return;
    }
    String status = switch (result.getStatus()) {
      case SUCCESSFUL -> "passed";
      case FAILED -> "failed";
      case ABORTED -> "skipped";
    };
    TamashReport.finishTest(testId(id), status);
  }

  @Override
  public void executionSkipped(TestIdentifier id, String reason) {
    if (id.isTest() && TamashReport.isEnabled()) {
      TamashReport.finishTest(testId(id), "skipped");
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    TamashReport.finish();
  }
}
