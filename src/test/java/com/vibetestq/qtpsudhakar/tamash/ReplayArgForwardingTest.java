package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A healed/recovered replay must re-issue the <b>entire</b> original call, not just its first
 * argument. Mirrors pw's {@code replay-second-arg} / {@code replay-multi-option} specs — the class
 * of bug where a trailing argument is silently dropped on replay, so the action reports success
 * while doing the wrong thing. Selenium's multi-arg surface is {@code sendKeys(CharSequence...)}
 * and the single-arg attribute getters.
 *
 * <p>Run: {@code mvn test -Dtest=ReplayArgForwardingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class ReplayArgForwardingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Form</h1>"
      + "<label for='username'>Username</label><input id='username' name='username'/>"
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
  void staleReplayForwardsEverySendKeysArgument(WebDriver driver) {
    driver.get(PAGE);
    WebElement username = driver.findElement(By.id("username"));
    username.sendKeys("x");
    driver.navigate().refresh();                       // username is now stale

    username.sendKeys("A", "B", "C");                  // stale -> re-find -> replay sendKeys(A,B,C)

    assertEquals("ABC",
        driver.findElement(By.id("username")).getDomProperty("value"),
        "all three CharSequence args must survive the replay");
  }

  @Test
  void staleReplayForwardsTheAttributeNameArgument(WebDriver driver) {
    driver.get(PAGE);
    WebElement username = driver.findElement(By.id("username"));
    username.sendKeys("hello");
    driver.navigate().refresh();                       // stale

    // getDomAttribute("name") on the stale ref -> re-find -> replay with the same "name" arg
    assertEquals("username", username.getDomAttribute("name"));
  }
}
