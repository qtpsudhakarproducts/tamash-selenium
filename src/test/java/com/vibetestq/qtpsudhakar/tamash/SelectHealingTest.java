package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code <select>} healing. Selenium's {@link Select} is a client-side utility — the only moment
 * tamash can heal is the {@code findElement} that locates the select itself; once healed, every
 * {@code Select} operation runs against the real element. (Playwright's {@code selectOption} is a
 * healable locator action, so its suite is larger; this is the equivalent Selenium surface.)
 *
 * <p>Run: {@code mvn test -Dtest=SelectHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class SelectHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Preferences</h1>"
      + "<label for='country'>Country</label>"
      + "<select id='country' name='country'>"
      + "<option>India</option><option>United States</option><option>Germany</option></select>"
      + "<label for='langs'>Languages</label>"
      + "<select id='langs' name='langs' multiple>"
      + "<option>Java</option><option>Kotlin</option><option>Groovy</option></select>"
      + "</body></html>";

  @BeforeAll
  static void ruleBased() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  private boolean healedInThisClass() {
    return Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .anyMatch(r -> r.testSelector != null && r.testSelector.contains("SelectHealingTest"));
  }

  @Test
  void healsABrokenSelectLocatorThenSelectByVisibleText(WebDriver driver) {
    driver.get(PAGE);
    By countryDropdown = By.id("country-picker");                 // broken — real id is #country
    Select country = new Select(driver.findElement(countryDropdown));
    country.selectByVisibleText("Germany");

    assertEquals("Germany",
        new Select(driver.findElement(By.id("country"))).getFirstSelectedOption().getText());
    assertTrue(healedInThisClass(), "the broken select locator should have healed");
  }

  @Test
  void healedMultiSelectSupportsMultipleSelections(WebDriver driver) {
    driver.get(PAGE);
    // The findElement is nested in a `Select x = ...` assignment, so the decoded description comes
    // from the outer variable — name it after the field ("Languages") so the rule-based matcher
    // has a label to match.
    Select languages = new Select(driver.findElement(By.cssSelector("#languages-field"))); // broken — real #langs
    assertTrue(languages.isMultiple());
    languages.selectByVisibleText("Java");
    languages.selectByVisibleText("Groovy");

    Select real = new Select(driver.findElement(By.id("langs")));
    assertEquals(2, real.getAllSelectedOptions().size());
    assertTrue(healedInThisClass());
  }
}
