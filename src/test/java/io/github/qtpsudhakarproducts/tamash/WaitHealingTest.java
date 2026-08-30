package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The enterprise pattern: a broken locator resolved <b>inside</b> a {@code WebDriverWait}. Proves
 * healing fires on the first poll and the wait then succeeds — and that the {@code HealCache}
 * keeps the wait's repeated polls to a single heal.
 *
 * <p>Run with: {@code mvn test -Dtest=WaitHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class WaitHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username' type='text'/>"
      + "</body></html>";

  @BeforeAll
  static void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @Test
  void healsInsideWebDriverWait(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#wrong-username");   // broken

    var wait = new WebDriverWait(driver, Duration.ofSeconds(5));
    wait.until(ExpectedConditions.visibilityOfElementLocated(usernameTextbox)).sendKeys("Admin");
    // second wait on the same broken locator — should be a cache hit, not a second heal
    wait.until(ExpectedConditions.elementToBeClickable(usernameTextbox));

    assertEquals("Admin", driver.findElement(By.id("username")).getAttribute("value"));

    var healed = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains("WaitHealingTest"))
        .toList();
    assertFalse(healed.isEmpty(), "expected the broken locator inside the wait to heal");
    assertTrue(healed.stream().allMatch(r -> r.getSuggestedSelector() != null
        && r.getSuggestedSelector().contains("username")), () -> healed.toString());

    long snapshotHeals = healed.stream().filter(r -> !"cache".equals(r.getProvider())).count();
    long cacheHeals = healed.stream().filter(r -> "cache".equals(r.getProvider())).count();
    assertTrue(snapshotHeals <= 2, "wait polling should not trigger repeated snapshot heals — got " + snapshotHeals);
    assertTrue(cacheHeals >= 1, "the wait's later polls should reuse the cached heal — got " + cacheHeals);
  }
}
