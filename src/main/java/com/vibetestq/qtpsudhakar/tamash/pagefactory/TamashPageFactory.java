package com.vibetestq.qtpsudhakar.tamash.pagefactory;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;

import java.lang.reflect.Constructor;

/**
 * Drop-in replacement for {@link PageFactory} that makes {@code @FindBy} fields self-healing.
 *
 * <pre>{@code
 * public class LoginPage {
 *   @FindBy(id = "username") private WebElement usernameTextbox;
 *   @FindBy(css = "button[type='submit']") private WebElement loginButton;
 *
 *   public LoginPage(WebDriver driver) {
 *     TamashPageFactory.initElements(driver, this);   // <- the only change
 *   }
 * }
 * }</pre>
 *
 * The {@code WebDriver} must be one obtained from an integration ({@code @UseTamashSelenium}, the
 * TestNG base class, the Cucumber hooks) or wrapped with {@code Bindings.bindDriver(...)} — but
 * even a raw driver works, {@code @FindBy} healing does not depend on the driver wrapper.
 */
public final class TamashPageFactory {
  private TamashPageFactory() {}

  /** Like {@link PageFactory#initElements(WebDriver, Object)} but with healing {@code @FindBy} proxies. */
  public static void initElements(WebDriver driver, Object page) {
    PageFactory.initElements(new TamashFieldDecorator(driver), page);
  }

  /** Like {@link PageFactory#initElements(WebDriver, Class)}: instantiates {@code pageClass}
   *  (a {@code WebDriver} constructor, else a no-arg one) then decorates it. */
  public static <T> T initElements(WebDriver driver, Class<T> pageClass) {
    T page = instantiate(driver, pageClass);
    initElements(driver, page);
    return page;
  }

  private static <T> T instantiate(WebDriver driver, Class<T> pageClass) {
    try {
      try {
        Constructor<T> withDriver = pageClass.getConstructor(WebDriver.class);
        return withDriver.newInstance(driver);
      } catch (NoSuchMethodException ignored) {
        Constructor<T> noArg = pageClass.getConstructor();
        return noArg.newInstance();
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("TamashPageFactory: could not instantiate " + pageClass.getName(), e);
    }
  }
}
