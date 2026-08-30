package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The explicit-hint escape hatch — a keyword-style wrapper that carries a logical element name but
 * doesn't put it at the call site. {@code Tamash.hint(name)} feeds it to the healer.
 *
 * <p>Run: {@code mvn test -Dtest=TamashHintTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class TamashHintTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Sign in</h1>"
      + "<label for='username'>Username</label><input id='username' name='username'/>"
      + "</body></html>";

  @BeforeAll
  static void provider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  /** A keyword-driven-style wrapper: the name comes from an Excel row / enum, not the call site. */
  static void enterTextByKeyword(WebDriver driver, String elementName, String selectorKey, String value) {
    By locator = By.cssSelector(selectorKey);   // selectorKey resolved from a locator map — here, broken
    try (var h = Tamash.hint(elementName)) {
      driver.findElement(locator).sendKeys(value);
    }
  }

  @Test
  void hintDrivesTheDescription(WebDriver driver) {
    driver.get(PAGE);
    // The wrapper's `locator` variable and selectorKey ("x9z1") decode to nothing useful —
    // only the hint "Username field" makes this healable by the rule-based provider.
    enterTextByKeyword(driver, "Username field", "#x9z1", "admin");

    assertEquals("admin", driver.findElement(By.id("username")).getAttribute("value"));

    SelfHealingReport r = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(x -> x.testSelector != null && x.testSelector.endsWith("#hintDrivesTheDescription"))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(r, "the hint should make the broken locator healable");
    assertEquals("Username field", r.getDescription());
    assertTrue(r.getSuggestedSelector().contains("By.id(\"username\")"), r.getSuggestedSelector());
  }

  @Test
  void hintClearsAfterScope(WebDriver driver) {
    driver.get(PAGE);
    try (var h = Tamash.hint("temporary")) {
      assertEquals("temporary", Tamash.currentHint());
    }
    assertNull(Tamash.currentHint(), "hint must not leak past its try-with-resources scope");
  }
}
