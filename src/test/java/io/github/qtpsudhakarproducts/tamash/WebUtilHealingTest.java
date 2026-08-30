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
 * The classic enterprise shape: a {@code WebUtil} that wraps {@code WebDriverWait}, called from a
 * Page Object that holds {@code By} fields. The broken locator's real name (`usernameTextbox`)
 * lives at the util's <b>caller</b>, not inside the util — so it exercises the multi-frame call-site
 * resolution.
 *
 * <p>Run with: {@code mvn test -Dtest=WebUtilHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class WebUtilHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username'/>"
      + "</body></html>";

  static final class WebUtil {
    static void type(WebDriver driver, By by, String text) {
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.visibilityOfElementLocated(by))
          .sendKeys(text);
    }
  }

  static final class LoginPage {
    private final WebDriver driver;
    private final By usernameTextbox = By.cssSelector("#user-name");   // broken — real id is #username

    LoginPage(WebDriver driver) { this.driver = driver; }

    void enterUsername(String value) {
      WebUtil.type(driver, usernameTextbox, value);
    }
  }

  @BeforeAll
  static void ruleBasedProvider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @Test
  void healsALocatorPassedThroughAWebUtil(WebDriver driver) {
    driver.get(PAGE);
    new LoginPage(driver).enterUsername("Admin");

    assertEquals("Admin", driver.findElement(By.id("username")).getAttribute("value"));

    SelfHealingReport r = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(x -> x.testSelector != null && x.testSelector.endsWith("#healsALocatorPassedThroughAWebUtil"))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(r, "the locator passed through WebUtil should heal");
    // the name was recovered from LoginPage's field/use, not from WebUtil's `by` param
    assertEquals("Username (textbox)", r.getDescription(), "description should decode the real field name");
    assertTrue(r.getSuggestedSelector().contains("By.id"), r.getSuggestedSelector());
  }
}
