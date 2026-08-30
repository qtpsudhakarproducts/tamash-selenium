package io.github.qtpsudhakarproducts.tamash.cucumber;

import org.openqa.selenium.WebDriver;

/**
 * Holder for the healing-wrapped {@link WebDriver} of the current Cucumber scenario. Cucumber's
 * default object factory doesn't inject across step-definition classes, so step classes read the
 * driver from here (static, per-thread — safe under {@code cucumber.execution.parallel}).
 *
 * <pre>{@code
 * import static io.github.qtpsudhakarproducts.tamash.cucumber.TamashSeleniumScenario.driver;
 *
 * @When("I sign in")
 * public void signIn() {
 *   driver().findElement(By.id("username")).sendKeys("Admin");
 * }
 * }</pre>
 */
public final class TamashSeleniumScenario {
  private TamashSeleniumScenario() {}

  private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

  /** The healing-wrapped driver for the current scenario. */
  public static WebDriver driver() {
    WebDriver d = DRIVER.get();
    if (d == null) {
      throw new IllegalStateException(
          "No driver for the current scenario — is TamashSeleniumCucumberHooks in your glue path?");
    }
    return d;
  }

  static void set(WebDriver driver) {
    DRIVER.set(driver);
  }

  static void clear() {
    DRIVER.remove();
  }
}
