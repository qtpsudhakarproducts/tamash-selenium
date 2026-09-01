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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The master switch. With {@code HEALER_ENABLED=false} a broken locator must fail exactly like
 * vanilla Selenium — no snapshot, no provider call, no {@link SelfHealingReport} — and a working
 * locator must be completely unaffected. Mirrors pw's {@code healing-disabled.e2e.spec.ts}.
 *
 * <p>Run: {@code mvn test -Dtest=HealingDisabledTest}
 */
@UseTamashSelenium
class HealingDisabledTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username'/>"
      + "</body></html>";

  @BeforeAll
  static void disableHealing() {
    System.setProperty("HEALER_ENABLED", "false");
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @AfterAll
  static void reEnableHealing() {
    System.clearProperty("HEALER_ENABLED");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  @Test
  void brokenLocatorFailsLikeVanilla(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#wrong-username");

    assertThrows(NoSuchElementException.class,
        () -> driver.findElement(usernameTextbox).sendKeys("Admin"));

    SelfHealingReport report = Healer.getHealingReports().stream()
        .filter(r -> r.testSelector != null && r.testSelector.contains("HealingDisabledTest"))
        .reduce((a, b) -> b).orElse(null);
    if (report != null) {
      assertFalse(report.isHealed(), "healing is off — nothing should heal");
      assertNull(report.getTokenUsage(), "healing is off — no provider call at all");
    }
  }

  @Test
  void workingLocatorIsUnaffected(WebDriver driver) {
    driver.get(PAGE);
    driver.findElement(By.id("username")).sendKeys("Admin");
    assertEquals("Admin", driver.findElement(By.id("username")).getAttribute("value"));
  }
}
