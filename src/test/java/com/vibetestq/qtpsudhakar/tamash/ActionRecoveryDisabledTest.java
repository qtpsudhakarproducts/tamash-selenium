package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.healer.HealCache;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The default: action recovery is <b>off</b> unless {@code HEALER_ACTION_RECOVERY_ENABLED=true}.
 * An intercepted click is not a selector problem, so with recovery off it must surface as a plain
 * {@link ElementClickInterceptedException} — the healer never silently swallows it. No AI needed.
 *
 * <p>Run: {@code mvn test -Pbrowser -Dtest=ActionRecoveryDisabledTest}
 */
@UseTamashSelenium
class ActionRecoveryDisabledTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Checkout</h1>"
      + "<div style='position:relative;width:200px;height:40px'>"
      + "<button id='pay' type='button' style='position:absolute;inset:0;width:100%;height:100%'"
      + " onclick=\"document.title='PAID'\">Pay now</button>"
      + "<div id='veil' style='position:absolute;inset:0;background:rgba(0,0,0,0.01)'></div>"
      + "</div></body></html>";

  @BeforeAll
  static void ruleBasedNoRecovery() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    System.clearProperty("HEALER_ACTION_RECOVERY_ENABLED");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  @Test
  void interceptedClickSurfacesAsItsRealException(WebDriver driver) {
    driver.get(PAGE);
    assertThrows(ElementClickInterceptedException.class,
        () -> driver.findElement(By.id("pay")).click());
    assertNotEquals("PAID", driver.getTitle(), "the click must not have gone through");
  }
}
