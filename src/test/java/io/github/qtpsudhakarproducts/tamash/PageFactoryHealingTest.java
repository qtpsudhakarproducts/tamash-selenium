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
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline end-to-end for the plug-and-play {@code @FindBy} path — a real browser, the rule-based
 * {@code tamash} provider, a Page Object initialised with <b>plain</b>
 * {@code PageFactory.initElements(wrappedDriver, this)} (no {@code TamashPageFactory}), with a
 * deliberately-broken {@code @FindBy} locator. Proves that wrapping the driver is enough.
 *
 * <p>Run with: {@code mvn test -Dtest=PageFactoryHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class PageFactoryHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username' type='text'/>"
      + "<button id='go'>Go</button></body></html>";

  static class LoginPage {
    @FindBy(css = "#wrong-username")   // broken — the real field is #username
    WebElement usernameTextbox;

    LoginPage(WebDriver driver) {
      PageFactory.initElements(driver, this);   // plain PageFactory — the driver is already wrapped
    }

    void enterUsername(String value) {
      usernameTextbox.sendKeys(value);
    }
  }

  @BeforeAll
  static void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @Test
  void healsABrokenFindByField(WebDriver driver) {
    driver.get(PAGE);

    new LoginPage(driver).enterUsername("Admin");

    assertEquals("Admin", driver.findElement(By.id("username")).getAttribute("value"));

    SelfHealingReport healed = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains(getClass().getSimpleName()))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(healed, "expected the broken @FindBy locator to heal");
    assertTrue(healed.getSuggestedSelector().contains("By.id"), healed.getSuggestedSelector());
  }
}
