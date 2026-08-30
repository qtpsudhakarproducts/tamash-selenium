package io.github.qtpsudhakarproducts.tamash.bindings;

import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.report.TamashReport;

import java.util.Set;

/** Bridges the binding proxy to {@link TamashReport}: turns an intercepted call (and, on failure,
 *  its {@link SelfHealingReport}) into a recorded step. Every method is a cheap no-op when the
 *  report isn't enabled. */
final class Steps {
  private Steps() {}

  private static final Set<String> READ_METHODS = Set.of(
      "getText", "getAttribute", "getDomAttribute", "getDomProperty", "getCssValue",
      "isDisplayed", "isEnabled", "isSelected", "getTagName", "getAccessibleName", "getAriaRole");

  static boolean enabled() {
    return TamashReport.isEnabled();
  }

  static String describeCallValue(String action, Object[] args) {
    Object v = firstArg(args);
    return switch (action) {
      case "sendKeys", "get", "to", "getAttribute", "getDomAttribute", "getDomProperty", "getCssValue" ->
          v == null ? null : stringify(v);
      default -> null;
    };
  }

  private static String stringify(Object v) {
    if (v instanceof Object[] arr) {
      StringBuilder sb = new StringBuilder();
      for (Object o : arr) sb.append(o);
      return sb.toString();
    }
    return String.valueOf(v);
  }

  private static Object firstArg(Object[] args) {
    return args != null && args.length > 0 ? args[0] : null;
  }

  static void recordSuccess(String action, String element, Object by, Object[] args, Object result, double durationMs) {
    if (!enabled()) {
      return;
    }
    TamashReport.Step s = new TamashReport.Step();
    s.action = action;
    s.element = element;
    s.locator = by == null ? null : String.valueOf(by);
    s.durationMs = durationMs;
    s.value = READ_METHODS.contains(action) && result != null ? String.valueOf(result) : describeCallValue(action, args);
    TamashReport.recordStep(s);
  }

  static void recordFailure(String action, String element, Object by, Object[] args, double durationMs,
                            SelfHealingReport report) {
    if (!enabled()) {
      return;
    }
    TamashReport.Step s = new TamashReport.Step();
    s.action = action;
    s.element = element;
    s.locator = by == null ? null : String.valueOf(by);
    s.value = describeCallValue(action, args);
    s.durationMs = durationMs;
    s.healed = report.isHealed();
    s.error = report.isHealed() ? null : report.getWarning();
    s.suggestedSelector = report.getSuggestedSelector();
    s.provider = report.getProvider();
    s.failureStage = report.getFailureStage();
    s.tokenUsage = report.getTokenUsage();
    s.usedVision = report.isUsedVision();
    s.usedActionRecovery = report.isUsedActionRecovery();
    s.needsReview = report.getNeedsReview();
    s.reviewNote = report.getReviewNote();
    if (!report.isHealed()) {
      s.ariaSnapshot = report.ariaSnapshotForReport;
    }
    TamashReport.recordStep(s);
  }

  static void recordSimple(String action, String element, Object[] args, double durationMs, Throwable error) {
    if (!enabled()) {
      return;
    }
    TamashReport.Step s = new TamashReport.Step();
    s.action = action;
    s.element = element;
    s.value = describeCallValue(action, args);
    s.durationMs = durationMs;
    s.error = error != null ? (error.getMessage() != null ? error.getMessage().split("\n", 2)[0] : error.toString()) : null;
    TamashReport.recordStep(s);
  }
}
