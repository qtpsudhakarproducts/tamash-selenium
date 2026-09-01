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
import org.openqa.selenium.WebElement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reported / cached selector must be one that actually resolves to the intended element —
 * never an unproven guess. Mirrors pw's {@code ref-report-accuracy-repro.spec.ts} for the common
 * (non-TOCTOU) case. The rare "derived, tried, threw, raw ref succeeded" trigger is a documented
 * gap here too — it could not be reproduced live (see TEST-PARITY-ROADMAP.md).
 *
 * <p>Run: {@code mvn test -Dtest=ReportCacheHonestyTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class ReportCacheHonestyTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Account</h1>"
      + "<label for='first'>First Name</label><input id='first' name='firstName'/>"
      + "<label for='last'>Last Name</label><input id='last' name='lastName'/>"
      + "<button id='go' type='button'>Continue</button>"
      + "</body></html>";

  private static final Pattern BY = Pattern.compile("By\\.(\\w+)\\(\"(.*)\"\\)");

  @BeforeAll
  static void ruleBased() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  private static By parse(String suggested) {
    Matcher m = BY.matcher(suggested);
    assertTrue(m.find(), "unparseable suggested selector: " + suggested);
    String v = m.group(2).replace("\\\"", "\"");
    return switch (m.group(1)) {
      case "id" -> By.id(v);
      case "name" -> By.name(v);
      case "cssSelector" -> By.cssSelector(v);
      case "xpath" -> By.xpath(v);
      case "className" -> By.className(v);
      case "linkText" -> By.linkText(v);
      case "tagName" -> By.tagName(v);
      default -> throw new AssertionError("unknown By kind: " + m.group(1));
    };
  }

  @Test
  void everyReportedSelectorIndependentlyResolvesToTheHealedElement(WebDriver driver) {
    driver.get(PAGE);

    // Variable names decode to "First Name" / "Continue Button" — matching the page's label and
    // button text, which is what the rule-based provider keys off. (Avoid nesting the findElement
    // in another assignment: the decoder would then read the outer variable's name.)
    WebElement firstName = driver.findElement(By.cssSelector("#first-name"));   // real: #first / name=firstName
    String firstValue = firstName.getDomAttribute("name");

    By continueButton = By.xpath("//button[normalize-space()='Submit']");       // real text: Continue
    driver.findElement(continueButton).click();

    var heals = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains("ReportCacheHonestyTest"))
        .toList();
    assertEquals(2, heals.size(), "both broken locators heal");

    for (SelfHealingReport r : heals) {
      String suggested = r.getSuggestedSelector();
      assertNotNull(suggested, "a healed report must carry the selector it used");
      WebElement resolved = driver.findElement(parse(suggested));   // must not throw
      assertNotNull(resolved);
    }

    assertEquals("firstName", firstValue, "the healed 'first name' locator resolved to the right input");
  }
}
