package com.vibetestq.qtpsudhakar.tamash;

import com.vibetestq.qtpsudhakar.tamash.bindings.Bindings;
import com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Bindings#getDurable} — resolve any locator (typically a brittle positional XPath) to a
 * durable, reusable equivalent, using the same derivation the healer uses. Mirrors pw's
 * {@code get-durable-*-repro.spec.ts}.
 *
 * <p>Run: {@code mvn test -Dtest=GetDurableTest}
 */
@UseTamashSelenium
class GetDurableTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body><h1>Account</h1><form>"
      + "<label for='username'>Username</label><input id='username' name='username'/>"
      + "<label for='email'>Email</label><input id='email' name='email' type='email'/>"
      + "<button id='save' type='submit'>Save</button>"
      + "</form></body></html>";

  @BeforeAll
  static void ruleBased() {
    System.setProperty("HEALER_PROVIDER", "tamash");
  }

  @Test
  void resolvesABrittleXpathToADurableBy(WebDriver driver) {
    driver.get(PAGE);
    By brittle = By.xpath("/html/body/form/input[1]");
    By durable = Bindings.getDurable(driver, brittle);

    assertNotNull(durable);
    assertNotEquals(brittle, durable);
    // both must resolve to the same element
    WebElement viaDurable = driver.findElement(durable);
    assertEquals("username", viaDurable.getDomAttribute("id"));
  }

  @Test
  void isStableAcrossRepeatedCalls_noSharedStateDrift(WebDriver driver) {
    driver.get(PAGE);
    By[] targets = {
        By.xpath("/html/body/form/input[1]"),
        By.xpath("/html/body/form/input[2]"),
        By.xpath("/html/body/form/button"),
    };
    By[] first = new By[3];
    for (int i = 0; i < 3; i++) first[i] = Bindings.getDurable(driver, targets[i]);
    // a second sweep must produce identical results — static derivation caches must not drift
    for (int i = 0; i < 3; i++) {
      assertEquals(first[i], Bindings.getDurable(driver, targets[i]), "call " + i + " drifted");
    }
    assertEquals("username", driver.findElement(first[0]).getDomAttribute("id"));
    assertEquals("email", driver.findElement(first[1]).getDomAttribute("id"));
    assertEquals("save", driver.findElement(first[2]).getDomAttribute("id"));
  }

  @Test
  void throwsWhenTheLocatorDoesNotResolve(WebDriver driver) {
    driver.get(PAGE);
    assertThrows(RuntimeException.class,
        () -> Bindings.getDurable(driver, By.cssSelector("#nothing-here")));
  }
}
