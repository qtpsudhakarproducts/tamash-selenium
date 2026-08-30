package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline end-to-end: a real browser, the rule-based {@code tamash} provider (zero tokens, no
 * network), a {@code data:} page, and a deliberately-broken locator. A pass proves the whole
 * runtime path — proxy binding → source-location + variable-name decode → DOM snapshot →
 * findRuleBasedMatch → ref resolution → deriveSuggestionFromElement → reflected replay.
 *
 * <p>Run with: {@code mvn test -Dtest=TamashRuleBasedHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class TamashRuleBasedHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body>"
      + "<h1>Sign in</h1>"
      + "<label for='username'>Username</label>"
      + "<input id='username' name='username' type='text'/>"
      + "<label for='password'>Password</label>"
      + "<input id='password' name='password' type='password'/>"
      + "<button id='login'>Login</button>"
      + "</body></html>";

  @BeforeAll
  static void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @Test
  void healsABrokenLocatorAtFindTime(WebDriver driver) {
    driver.get(PAGE);

    // Wrong id — the real field is #username. The variable name decodes to "Username (textbox)",
    // which the rule-based provider matches against the DOM snapshot.
    WebElement usernameInput = driver.findElement(By.cssSelector("#user-name"));
    usernameInput.sendKeys("Admin");

    assertEquals("Admin", driver.findElement(By.id("username")).getAttribute("value"));

    SelfHealingReport healed = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains(getClass().getSimpleName()))
        .reduce((a, b) -> b)
        .orElse(null);
    assertNotNull(healed, "expected a healed locator");
    assertTrue(healed.getSuggestedSelector() != null && healed.getSuggestedSelector().contains("By."),
        () -> "suggested: " + (healed == null ? null : healed.getSuggestedSelector()));
  }
}
