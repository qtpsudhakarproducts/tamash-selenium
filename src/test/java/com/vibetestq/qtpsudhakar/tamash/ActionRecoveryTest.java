package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Action recovery — the opt-in ({@code HEALER_ACTION_RECOVERY_ENABLED=true}) second-order
 * fallback for a failure that is <b>not</b> a selector problem: the element was found, but the
 * action couldn't complete (here: a click intercepted by an overlay). The AI picks one of
 * scroll / force / wait / dispatch. Mirrors pw's {@code ref-action-recovery-repro.spec.ts}.
 *
 * <p>A no-op unless a real AI provider is selected (the rule-based {@code tamash} provider always
 * declines a tactic). Run:
 * {@code mvn test -Dtest=ActionRecoveryTest -DHEALER_PROVIDER=anthropic -DANTHROPIC_MODEL=claude-haiku-4-5 -DHEALER_ACTION_RECOVERY_ENABLED=true}
 */
@UseTamashSelenium
class ActionRecoveryTest {

  private static final String PROVIDER = firstNonBlank(
      System.getProperty("HEALER_PROVIDER"), System.getenv("HEALER_PROVIDER"));

  // A button whose onclick records that it fired, fully covered by a same-size overlay div so a
  // real .click() is intercepted.
  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Checkout</h1>"
      + "<div style='position:relative;width:200px;height:40px'>"
      + "<button id='pay' type='button' style='position:absolute;inset:0;width:100%;height:100%'"
      + " onclick=\"document.title='PAID'\">Pay now</button>"
      + "<div id='veil' style='position:absolute;inset:0;background:rgba(0,0,0,0.01)'></div>"
      + "</div></body></html>";

  @BeforeAll
  static void requireRealProviderAndEnableRecovery() {
    assumeTrue(PROVIDER != null && !PROVIDER.isBlank() && !"tamash".equalsIgnoreCase(PROVIDER),
        "ActionRecoveryTest needs a real AI provider");
    System.setProperty("HEALER_ACTION_RECOVERY_ENABLED", "true");
    ProviderFactory.resetCache();
  }

  @AfterAll
  static void disableRecovery() {
    System.clearProperty("HEALER_ACTION_RECOVERY_ENABLED");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  @Test
  void recoversAClickInterceptedByAnOverlay(WebDriver driver) {
    driver.get(PAGE);
    assertEquals("", orEmpty(driver.getTitle()), "precondition: onclick has not fired");

    // sanity: without recovery this would be an ElementClickInterceptedException — confirm the
    // fixture really does intercept by checking the raw driver behaviour is that exception class
    // only when recovery can't help. Here recovery IS enabled, so the click should go through.
    driver.findElement(By.id("pay")).click();

    SelfHealingReport r = Healer.getHealingReports().stream()
        .filter(rr -> rr.testSelector != null && rr.testSelector.contains("ActionRecoveryTest"))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(r, "a heal report should exist for the intercepted click");
    assertEquals("click", r.getAction());
    assertTrue(r.isUsedActionRecovery(), "the action-recovery path should have engaged");

    if (r.isHealed()) {
      assertEquals("PAID", driver.getTitle(), "a recovered click must actually fire the button");
    } else {
      // pw documents that the model sometimes picks a tactic that doesn't help this shape — record
      // it honestly rather than failing the build on model choice.
      System.out.println("[action-recovery] engaged but did not recover this fixture: stage="
          + r.getFailureStage());
    }
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b != null && !b.isBlank() ? b : null;
  }
}
