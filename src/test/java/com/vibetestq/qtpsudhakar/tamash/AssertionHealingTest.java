package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assertion-context behaviour:
 *  - a content assertion's broken locator heals (that's the point) and is flagged;
 *  - an "assert absent" ({@code assertThrows(NoSuchElementException…)}) is NOT healed;
 *  - {@code HEALER_ASSERTIONS=strict} disables healing inside any assertion.
 *
 * <p>Run with: {@code mvn test -Dtest=AssertionHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class AssertionHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username' value='admin'/>"
      + "</body></html>";

  @BeforeAll
  static void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @AfterEach
  void resetMode() {
    System.clearProperty("HEALER_ASSERTIONS");
  }

  private SelfHealingReport myLastHeal() {
    String method = Thread.currentThread().getStackTrace()[2].getMethodName();
    return Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.endsWith("#" + method))
        .reduce((a, b) -> b).orElse(null);
  }

  @Test
  void contentAssertion_healsTheBrokenLocator(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#user-name");   // broken — real id is #username
    assertEquals("admin", driver.findElement(usernameTextbox).getAttribute("value"));

    SelfHealingReport r = myLastHeal();
    assertNotNull(r, "a content assertion's broken locator should heal");
    assertTrue(r.healedInAssertion, "should be flagged as healed-in-assertion");
  }

  @Test
  void assertAbsent_isNotHealed(WebDriver driver) {
    driver.get(PAGE);
    assertThrows(NoSuchElementException.class,
        () -> driver.findElement(By.id("row-that-is-really-gone")));
    assertNull(myLastHeal(), "an assertThrows(NoSuchElementException…) must not be healed");
  }

  @Test
  void strictMode_disablesHealingInsideAssertions(WebDriver driver) {
    System.setProperty("HEALER_ASSERTIONS", "strict");
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#user-name");   // broken
    assertThrows(NoSuchElementException.class,
        () -> assertEquals("admin", driver.findElement(usernameTextbox).getAttribute("value")));
    assertNull(myLastHeal(), "HEALER_ASSERTIONS=strict must not heal inside an assertion");
  }
}
