package io.github.qtpsudhakarproducts.tamash.cucumber;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import io.github.qtpsudhakarproducts.tamash.CurrentTest;
import io.github.qtpsudhakarproducts.tamash.SeleniumLifecycle;
import io.github.qtpsudhakarproducts.tamash.TamashHeals;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.report.TamashReport;

import java.nio.charset.StandardCharsets;

/**
 * Cucumber glue for tamash-selenium — the counterpart of JUnit 5's {@code @UseTamashSelenium}.
 * Add this package to your Cucumber {@code glue} and every scenario gets a healing-wrapped
 * {@link TamashSeleniumScenario#driver()}, its self-heal reports attached to the scenario, and
 * {@code apply-heals} test tracking / the HTML step report.
 *
 * <pre>{@code
 * @Suite
 * @IncludeEngines("cucumber")
 * @SelectClasspathResource("features")
 * @ConfigurationParameter(key = GLUE_PROPERTY_NAME,
 *     value = "com.acme.steps,io.github.qtpsudhakarproducts.tamash.cucumber")
 * class RunCucumberTest {}
 * }</pre>
 */
public class TamashSeleniumCucumberHooks {

  private static volatile SeleniumLifecycle.Session session;
  private SeleniumLifecycle.Scope scope;

  @BeforeAll
  public static void tamashBeforeAll() {
    TamashReport.enableIfConfigured();
    session = SeleniumLifecycle.launch();
  }

  @AfterAll
  public static void tamashAfterAll() {
    SeleniumLifecycle.close(session);
    session = null;
    TamashReport.finish();
  }

  @Before(order = 0)
  public void tamashBefore(Scenario scenario) {
    scope = SeleniumLifecycle.openScope(session);
    TamashSeleniumScenario.set(scope.driver());

    String id = scenarioId(scenario);
    CurrentTest.set(new CurrentTest.Info(id, null, scenario.getName()));
    TamashReport.setCurrentTest(id);
    TamashReport.startTest(id);
  }

  @After(order = 0)
  public void tamashAfter(Scenario scenario) {
    String id = scenarioId(scenario);
    try {
      var reports = TamashHeals.forTest(id);
      if (!reports.isEmpty()) {
        scenario.attach(TamashHeals.toJson(reports).getBytes(StandardCharsets.UTF_8),
            "application/json", "tamash-self-healing.json");
        scenario.log("[tamash] " + TamashHeals.summary(reports));
        for (SelfHealingReport r : reports) {
          if (!r.isHealed() && r.ariaSnapshotForReport != null) {
            scenario.attach(r.ariaSnapshotForReport.getBytes(StandardCharsets.UTF_8),
                "text/plain", "tamash-dom-" + r.getAction() + ".txt");
          }
        }
      }
      TamashReport.finishTest(id, scenario.isFailed() ? "failed" : "passed");
    } finally {
      CurrentTest.clear();
      io.github.qtpsudhakarproducts.tamash.healer.HealCache.clear();
    io.github.qtpsudhakarproducts.tamash.Tamash.clearHint();
      TamashReport.setCurrentTest(null);
      TamashSeleniumScenario.clear();
      SeleniumLifecycle.closeScope(scope);
    }
  }

  private static String scenarioId(Scenario scenario) {
    return scenario.getUri() + "#" + scenario.getName();
  }
}
