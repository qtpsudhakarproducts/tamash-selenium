package io.github.qtpsudhakarproducts.tamash;

import io.cucumber.core.cli.Main;
import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.smoke.TestNgSmoke;
import io.github.qtpsudhakarproducts.tamash.testng.TamashSeleniumTestNgListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testng.TestNG;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the TestNG base class and the Cucumber hooks programmatically (from a plain JUnit test)
 * and asserts a heal actually happened + was attributed to the right test/scenario — proving the
 * non-JUnit integrations wire {@code Bindings.bindDriver}, {@code CurrentTest}, and the heal log
 * exactly like {@code @UseTamashSelenium} does.
 *
 * <p>Excluded from the default surefire run (needs a browser); run with
 * {@code mvn test -Dtest=FrameworkIntegrationTest}.
 */
class FrameworkIntegrationTest {

  @BeforeEach
  void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  private static SelfHealingReport healedFor(String suffix) {
    return Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> "sendKeys".equals(r.getAction()) || "findElement".equals(r.getAction()))
        .filter(r -> r.testSelector != null && r.testSelector.contains(suffix))
        .reduce((a, b) -> b)
        .orElse(null);
  }

  @Test
  void testNgBaseClassHealsAndAttributes() {
    TestNG tng = new TestNG();
    tng.setUseDefaultListeners(false);
    tng.setVerbose(0);
    tng.addListener(new TamashSeleniumTestNgListener());
    tng.setTestClasses(new Class[]{TestNgSmoke.class});
    tng.run();

    assertFalse(tng.hasFailure(), "TestNG smoke scenario should pass (healed)");
    SelfHealingReport r = healedFor("TestNgSmoke#healsABrokenLocator");
    assertNotNull(r, "expected a healed locator attributed to the TestNG test");
    assertTrue(r.getSuggestedSelector().contains("By."), r.getSuggestedSelector());
  }

  @Test
  void cucumberHooksHealAndAttribute() {
    byte exit = Main.run(new String[]{
        "--glue", "io.github.qtpsudhakarproducts.tamash.smoke",
        "--glue", "io.github.qtpsudhakarproducts.tamash.cucumber",
        "src/test/resources/features/tamash-smoke.feature",
    }, Thread.currentThread().getContextClassLoader());

    assertEquals(0, exit, "Cucumber smoke scenario should pass (healed)");
    SelfHealingReport r = healedFor("tamash-smoke.feature#heals a broken locator inside a step");
    assertNotNull(r, "expected a healed locator attributed to the Cucumber scenario");
    assertTrue(r.getSuggestedSelector().contains("By."), r.getSuggestedSelector());
  }
}
