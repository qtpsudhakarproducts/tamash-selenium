package com.vibetestq.qtpsudhakar.tamash.testng;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.SeleniumLifecycle;
import com.vibetestq.qtpsudhakar.tamash.report.TamashReport;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

/**
 * TestNG base class — the counterpart of JUnit 5's {@code @UseTamashSelenium}. Extend it and use
 * the {@link #driver} field; healing, {@code apply-heals} test tracking, and the HTML step report
 * all work (the latter two via {@link TamashSeleniumTestNgListener}, auto-registered — no
 * {@code @Listeners} needed).
 *
 * <pre>{@code
 * public class LoginTest extends TamashSeleniumTestNgTest {
 *   @Test
 *   public void logsIn() {
 *     driver.get("https://the-internet.herokuapp.com/login");
 *     driver.findElement(By.id("username")).sendKeys("tomsmith"); // healed if it breaks
 *   }
 * }
 * }</pre>
 */
public abstract class TamashSeleniumTestNgTest {

  private SeleniumLifecycle.Session session;
  private SeleniumLifecycle.Scope scope;

  /** The healing-wrapped driver for the current test method. */
  protected WebDriver driver;

  @BeforeClass(alwaysRun = true)
  public void tamashLaunch() {
    session = SeleniumLifecycle.launch();
  }

  @AfterClass(alwaysRun = true)
  public void tamashClose() {
    SeleniumLifecycle.close(session);
  }

  @BeforeMethod(alwaysRun = true)
  public void tamashOpen(Method testMethod) {
    scope = SeleniumLifecycle.openScope(session);
    driver = scope.driver();
    String id = getClass().getName() + "#" + testMethod.getName();
    CurrentTest.set(new CurrentTest.Info(getClass().getName(), testMethod.getName(), testMethod.getName()));
    TamashReport.setCurrentTest(id);
  }

  @AfterMethod(alwaysRun = true)
  public void tamashCloseMethod() {
    com.vibetestq.qtpsudhakar.tamash.healer.HealCache.clear();
    com.vibetestq.qtpsudhakar.tamash.Tamash.clearHint();
    SeleniumLifecycle.closeScope(scope);
  }
}
