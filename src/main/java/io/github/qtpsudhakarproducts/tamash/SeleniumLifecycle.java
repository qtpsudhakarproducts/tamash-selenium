package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.bindings.Bindings;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.time.Duration;

/**
 * The browser launch → per-test scope lifecycle, shared by every framework integration
 * ({@code @UseTamashSelenium} for JUnit 5, {@code TamashSeleniumTestNgTest} for TestNG,
 * {@code TamashSeleniumCucumberHooks} for Cucumber) so all three behave identically.
 *
 * <p>Isolation parity with Playwright's fresh {@code BrowserContext} per test: a brand-new
 * {@link WebDriver} per test method by default. {@code TAMASH_REUSE_DRIVER=true} reuses one driver
 * per test class instead (cookies + storage cleared between methods) — faster, slightly weaker
 * isolation. Selenium's implicit wait is pinned to 0 so a broken {@code findElement} surfaces
 * immediately for the healer (the equivalent of the Playwright port capping the action timeout).
 */
public final class SeleniumLifecycle {
  private SeleniumLifecycle() {}

  /** A driver held for the whole test class — non-null only when {@code TAMASH_REUSE_DRIVER=true}. */
  public record Session(WebDriver classDriver) {}

  /** {@code rawDriver} is what teardown quits (per-method) or resets (reuse); {@code driver} is
   *  the healing-wrapped view the test uses. */
  public record Scope(WebDriver rawDriver, WebDriver driver, boolean owned) {}

  public static Session launch() {
    return new Session(reuseDriver() ? newDriver() : null);
  }

  public static void close(Session session) {
    if (session != null && session.classDriver() != null) {
      quietQuit(session.classDriver());
    }
  }

  public static Scope openScope(Session session) {
    boolean reuse = reuseDriver();
    WebDriver raw = reuse && session.classDriver() != null ? session.classDriver() : newDriver();
    if (reuse) {
      resetForReuse(raw);
    }
    raw.manage().timeouts().implicitlyWait(Duration.ZERO);
    WebDriver bound = Bindings.bindDriver(raw);
    return new Scope(raw, bound, !reuse);
  }

  public static void closeScope(Scope scope) {
    if (scope != null && scope.owned()) {
      quietQuit(scope.rawDriver());
    }
  }

  // ---- driver construction --------------------------------------------

  static WebDriver newDriver() {
    String browser = envOr("TAMASH_BROWSER", "chrome").trim().toLowerCase();
    boolean headless = isHeadless();
    return switch (browser) {
      case "firefox" -> {
        FirefoxOptions o = new FirefoxOptions();
        if (headless) o.addArguments("-headless");
        yield new org.openqa.selenium.firefox.FirefoxDriver(o);
      }
      case "edge" -> {
        EdgeOptions o = new EdgeOptions();
        if (headless) o.addArguments("--headless=new", "--disable-gpu");
        yield new org.openqa.selenium.edge.EdgeDriver(o);
      }
      default -> {
        ChromeOptions o = new ChromeOptions();
        if (headless) o.addArguments("--headless=new", "--disable-gpu");
        o.addArguments("--window-size=1280,900", "--no-sandbox", "--disable-dev-shm-usage");
        yield new org.openqa.selenium.chrome.ChromeDriver(o);
      }
    };
  }

  private static void resetForReuse(WebDriver driver) {
    try {
      driver.get("about:blank");
      driver.manage().deleteAllCookies();
      if (driver instanceof org.openqa.selenium.JavascriptExecutor js) {
        js.executeScript("try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}");
      }
    } catch (Exception ignored) {
      // best-effort
    }
  }

  private static void quietQuit(WebDriver driver) {
    try {
      driver.quit();
    } catch (Exception ignored) {
      // best-effort
    }
  }

  public static boolean reuseDriver() {
    String v = Env.get("TAMASH_REUSE_DRIVER");
    return v != null && v.trim().equalsIgnoreCase("true");
  }

  public static boolean isHeadless() {
    String value = Env.get("HEADLESS");
    return value == null || !value.equalsIgnoreCase("false");
  }

  private static String envOr(String key, String fallback) {
    String v = Env.get(key);
    return (v == null || v.isBlank()) ? fallback : v;
  }
}
