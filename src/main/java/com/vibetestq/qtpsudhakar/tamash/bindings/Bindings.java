package com.vibetestq.qtpsudhakar.tamash.bindings;

import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Public entry points for wrapping a {@link WebDriver} (and, transitively, every {@link WebElement}
 * it finds) with self-healing, plus {@code unwrap} and {@code getDurable}.
 *
 * <p>Unlike the Playwright port there is no {@code assertThat} shim — Selenium has no built-in
 * assertion library, and a healing-wrapped {@code WebElement} satisfies any JUnit / AssertJ /
 * Hamcrest / TestNG assertion directly because it implements the same {@code WebElement} interface.
 * {@link #unwrap} stays for the rare concrete cast (e.g. {@code (RemoteWebElement)}).
 */
public final class Bindings {
  private Bindings() {}

  /** Wrap a driver so it — and every element found through it — is healing-aware. The framework
   *  integrations ({@code @UseTamashSelenium}, the TestNG base class, the Cucumber hooks) call
   *  this for you. */
  public static WebDriver bindDriver(WebDriver driver) {
    return HealingInvocationHandler.wrapDriver(driver);
  }

  /** Wrap a single element (rarely needed directly — elements found through a bound driver are
   *  already wrapped). */
  public static WebElement bindElement(WebElement element) {
    return HealingInvocationHandler.wrapElement(element, null, null, new Throwable(), List.of());
  }

  /** Returns the real, unwrapped Selenium object behind a healing wrapper. If the argument isn't
   *  wrapped, it's returned unchanged. */
  public static <T> T unwrap(T maybeWrapped) {
    return HealingInvocationHandler.unwrap(maybeWrapped);
  }

  /**
   * Resolves a {@link By} to a durable equivalent using the same derivation logic self-healing
   * uses internally — most useful on a brittle XPath / positional selector. {@code action} is
   * optional context ({@code "sendKeys"}, {@code "click"}, …) used to guess a control kind when
   * the element's own role is generic. Throws if nothing durable could be derived.
   */
  public static By getDurable(WebDriver driver, By by) {
    return getDurable(driver, by, null);
  }

  public static By getDurable(WebDriver driver, By by, String action) {
    return Healer.getDurableLocator(unwrap(driver), by, action, List.of());
  }

  /** The decoded human label for a locator at the given captured call site — used by the
   *  {@code @FindBy} nested-find path. Internal. */
  public static String describeCallSite(Throwable callSite, By by) {
    return HealingInvocationHandler.describeCallSite(callSite, by);
  }
}
