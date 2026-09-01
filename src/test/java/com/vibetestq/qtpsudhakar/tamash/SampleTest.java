package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real end-to-end against a live site with a real AI provider. Excluded from the default surefire
 * run — set up a provider ({@code HEALER_PROVIDER} + key) and run:
 *
 * <pre>mvn test -Dtest=SampleTest</pre>
 */
@UseTamashSelenium
class SampleTest {

  @Test
  void healsABrokenSelectorOnALiveSite(WebDriver driver) {
    driver.get("https://the-internet.herokuapp.com/login");

    // Deliberately broken — the real field is #username.
    WebElement usernameTextbox = driver.findElement(By.cssSelector("#user-name"));
    usernameTextbox.sendKeys("tomsmith");
    driver.findElement(By.id("password")).sendKeys("SuperSecretPassword!");
    driver.findElement(By.cssSelector("button[type='submit']")).click();

    assertTrue(driver.findElement(By.cssSelector(".flash")).getText().contains("You logged into a secure area"));

    SelfHealingReport healed = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.contains(getClass().getSimpleName()))
        .reduce((a, b) -> b).orElse(null);
    assertNotNull(healed, "expected the broken #user-name selector to heal");
  }
}
