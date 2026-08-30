package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import org.json.JSONArray;

import java.util.List;

/**
 * Shared helpers for surfacing a test's self-heal reports — used by the JUnit extension
 * ({@code publishFile}), the TestNG listener ({@code Reporter.log} + a file), and the Cucumber
 * hooks ({@code Scenario.attach}). Each integration attaches the artifacts its framework
 * understands; this class just produces them.
 */
public final class TamashHeals {
  private TamashHeals() {}

  /** Every self-heal report recorded for {@code testId} (as stamped by
   *  {@link CurrentTest} → {@code SelfHealingReport.testSelector}). */
  public static List<SelfHealingReport> forTest(String testId) {
    if (testId == null) {
      return List.of();
    }
    return Healer.getHealingReports().stream()
        .filter(r -> testId.equals(r.testSelector))
        .toList();
  }

  public static boolean any(String testId) {
    return !forTest(testId).isEmpty();
  }

  /** Pretty JSON array of the reports — the {@code tamash-self-healing.json} attachment body. */
  public static String toJson(List<SelfHealingReport> reports) {
    JSONArray arr = new JSONArray();
    for (SelfHealingReport r : reports) {
      arr.put(r.toJson());
    }
    return arr.toString(2);
  }

  /** One-line summary, e.g. {@code "2 healed, 1 not healed"}. */
  public static String summary(List<SelfHealingReport> reports) {
    long healed = reports.stream().filter(SelfHealingReport::isHealed).count();
    return healed + " healed, " + (reports.size() - healed) + " not healed";
  }
}
