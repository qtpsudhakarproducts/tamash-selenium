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

import static org.junit.jupiter.api.Assertions.*;

/**
 * A broken locator resolved <b>inside an {@code <iframe>}</b>. Selenium's {@code switchTo().frame}
 * is stateful — once switched, the wrapped driver's {@code findElement} (and the healer's DOM
 * snapshot, which runs {@code executeScript} in the focused browsing context) both operate inside
 * the frame document. Mirrors pw's {@code iframe-ref-repro.spec.ts}.
 *
 * <p>Run: {@code mvn test -Dtest=IframeHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class IframeHealingTest {

  // srcdoc keeps everything in one navigation; &quot; are the inner attribute quotes.
  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Outer page</h1>"
      + "<iframe id='inner' width='400' height='200' srcdoc='"
      + "<label for=&quot;n&quot;>Full Name</label>"
      + "<input id=&quot;n&quot; name=&quot;fullname&quot; type=&quot;text&quot;>"
      + "'></iframe>"
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

  @Test
  void healsInsideAFrameThenReturnsToDefaultContent(WebDriver driver) {
    driver.get(PAGE);
    driver.switchTo().frame("inner");

    By nameTextbox = By.cssSelector("#full-name");                 // broken — real is #n / name=fullname
    driver.findElement(nameTextbox).sendKeys("Ada Lovelace");
    assertEquals("Ada Lovelace",
        driver.findElement(By.name("fullname")).getAttribute("value"));

    driver.switchTo().defaultContent();
    assertTrue(driver.findElement(By.tagName("h1")).getText().contains("Outer page"));

    SelfHealingReport healed = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains("IframeHealingTest"))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(healed, "the broken locator inside the frame should have healed");
    String suggested = healed.getSuggestedSelector();
    // id wins the derivation ladder, so By.id("n") is expected; By.name("fullname") is also valid.
    assertTrue(suggested != null && (suggested.contains("\"n\"") || suggested.contains("fullname")),
        () -> "suggested: " + suggested);
  }
}
