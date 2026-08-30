package io.github.qtpsudhakarproducts.tamash;

import io.github.qtpsudhakarproducts.tamash.healer.Healer;
import io.github.qtpsudhakarproducts.tamash.healer.HealCache;
import io.github.qtpsudhakarproducts.tamash.healer.SelfHealingReport;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.junit.UseTamashSelenium;
import io.github.qtpsudhakarproducts.tamash.pagefactory.TamashPageFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.pagefactory.AjaxElementLocatorFactory;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A gauntlet of real-world Selenium patterns, each with a deliberately-broken locator, run against
 * the rule-based {@code tamash} provider. Reports which patterns heal.
 *
 * <p>Run: {@code mvn test -Dtest=PatternsHealingTest -DHEALER_PROVIDER=tamash}
 */
@UseTamashSelenium
class PatternsHealingTest {

  private static final String PAGE = "data:text/html,"
      + "<html><body>"
      + "<h1>Account</h1>"
      + "<form>"
      + "  <label for='username'>Username</label><input id='username' name='username'/>"
      + "  <label for='password'>Password</label><input id='password' name='password' type='password'/>"
      + "  <label for='country'>Country</label>"
      + "  <select id='country' name='country'><option>India</option><option>United States</option></select>"
      + "  <button id='save' type='button'>Save</button>"
      + "</form>"
      + "<nav><a href='/home' id='home-link'>Home</a> <a href='/help' id='help-link'>Help</a></nav>"
      + "<div id='panel'><label for='nickname'>Nickname</label><input id='nickname' name='nickname'/></div>"
      + "<div id='status'>Ready</div>"
      + "</body></html>";

  @BeforeAll
  static void provider() {
    System.setProperty("HEALER_PROVIDER", "tamash");
    ProviderFactory.resetCache();
  }

  @BeforeEach
  void freshCache() {
    HealCache.clear();
  }

  private SelfHealingReport healOf(String testMethod) {
    return Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.endsWith("#" + testMethod))
        .reduce((a, b) -> b).orElse(null);
  }

  private void assertHealed(String testMethod, String expectContains) {
    SelfHealingReport r = healOf(testMethod);
    assertNotNull(r, testMethod + ": expected a heal");
    assertTrue(r.getSuggestedSelector() != null && r.getSuggestedSelector().contains(expectContains),
        testMethod + ": " + (r == null ? null : r.getSuggestedSelector()));
  }

  // ---- 1. Fluent Page Object with By fields ----------------------------

  static final class AccountPage {
    private final WebDriver driver;
    private final By usernameTextbox = By.cssSelector("#user-name");     // broken
    private final By passwordTextbox = By.id("password");
    private final By saveButton = By.id("save");
    AccountPage(WebDriver driver) { this.driver = driver; }
    AccountPage username(String v) { driver.findElement(usernameTextbox).sendKeys(v); return this; }
    AccountPage password(String v) { driver.findElement(passwordTextbox).sendKeys(v); return this; }
    void save() { driver.findElement(saveButton).click(); }
  }

  @Test
  void fluentPageObject(WebDriver driver) {
    driver.get(PAGE);
    new AccountPage(driver).username("admin").password("secret").save();
    assertEquals("admin", driver.findElement(By.id("username")).getAttribute("value"));
    assertHealed("fluentPageObject", "By.id");
  }

  // ---- 2. Select dropdown -------------------------------------------

  @Test
  void selectDropdown(WebDriver driver) {
    driver.get(PAGE);
    By countryDropdown = By.id("country-picker");                        // broken
    new Select(driver.findElement(countryDropdown)).selectByVisibleText("United States");
    assertEquals("United States", new Select(driver.findElement(By.id("country"))).getFirstSelectedOption().getText());
    assertHealed("selectDropdown", "By.");
  }

  // ---- 3. Actions API ---------------------------------------------

  @Test
  void actionsApi(WebDriver driver) {
    driver.get(PAGE);
    By saveButton = By.cssSelector("button.save-btn");                   // broken
    new Actions(driver).moveToElement(driver.findElement(saveButton)).click().perform();
    assertHealed("actionsApi", "By.");
  }

  // ---- 4. Stale element re-heal ----------------------------------

  @Test
  void staleElementReHeal(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#user-name");                  // broken
    WebElement username = driver.findElement(usernameTextbox);          // heals here
    username.sendKeys("first");
    driver.navigate().refresh();                                        // username element is now stale
    username.sendKeys("second");                                        // re-find with original By, then heal
    assertEquals("second", driver.findElement(By.id("username")).getAttribute("value"));
    assertHealed("staleElementReHeal", "By.id");
  }

  // ---- 5. @FindBy + AjaxElementLocatorFactory (custom decorator) --

  static final class PanelPage {
    @FindBy(id = "nick-name") WebElement nicknameTextbox;               // broken — real id is #nickname
    PanelPage(WebDriver driver) {
      org.openqa.selenium.support.PageFactory.initElements(
          new io.github.qtpsudhakarproducts.tamash.pagefactory.TamashFieldDecorator(
              driver, new AjaxElementLocatorFactory(driver, 3)), this);
    }
  }

  @Test
  void findByWithAjaxLocatorFactory(WebDriver driver) {
    driver.get(PAGE);
    new PanelPage(driver).nicknameTextbox.sendKeys("Ace");
    assertEquals("Ace", driver.findElement(By.id("nickname")).getAttribute("value"));
    assertHealed("findByWithAjaxLocatorFactory", "By.id");
  }

  // ---- 6. Nested @FindBy container -> child find -----------------

  static final class NavPage {
    @FindBy(tagName = "nav") WebElement navBar;
    NavPage(WebDriver driver) { TamashPageFactory.initElements(driver, this); }
  }

  @Test
  void nestedFindOnContainer(WebDriver driver) {
    driver.get(PAGE);
    NavPage nav = new NavPage(driver);
    By helpLink = By.linkText("Support");                               // broken — link text is "Help"
    nav.navBar.findElement(helpLink).click();
    assertHealed("nestedFindOnContainer", "By.");
  }

  // ---- 7. Parameterized / repeated — cache should kick in --------

  @ParameterizedTest
  @ValueSource(strings = {"a", "b", "c"})
  void parameterizedRepeatsUseCache(String v, WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#user-name");                  // broken, same every iteration
    driver.findElement(usernameTextbox).sendKeys(v);
    assertHealed("parameterizedRepeatsUseCache", "By.id");
  }

  // ---- 8. Multiple broken locators in one test ------------------

  @Test
  void multipleBrokenLocators(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.id("user-name");                            // broken 1
    By passwordTextbox = By.cssSelector("input.pwd");                   // broken 2
    By saveButton = By.cssSelector("button.submit");                    // broken 3
    driver.findElement(usernameTextbox).sendKeys("x");
    driver.findElement(passwordTextbox).sendKeys("y");
    driver.findElement(saveButton).click();
    long heals = Healer.getHealingReports().stream()
        .filter(SelfHealingReport::isHealed)
        .filter(r -> r.testSelector != null && r.testSelector.endsWith("#multipleBrokenLocators"))
        .count();
    assertEquals(3, heals, "all three broken locators should heal independently");
  }

  // ---- 9. FluentWait ------------------------------------------

  @Test
  void fluentWait(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.id("user-name");                            // broken
    WebElement el = new FluentWait<>(driver)
        .withTimeout(Duration.ofSeconds(5))
        .pollingEvery(Duration.ofMillis(200))
        .ignoring(NoSuchElementException.class)
        .until(d -> d.findElement(usernameTextbox));
    el.sendKeys("via-fluent-wait");
    assertHealed("fluentWait", "By.id");
  }

  // ---- 10. Wait for presence, then act (the recommended shape) --

  @Test
  void waitForPresenceThenAct(WebDriver driver) {
    driver.get(PAGE);
    By usernameTextbox = By.cssSelector("#user-name");                  // broken
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.presenceOfElementLocated(usernameTextbox));
    driver.findElement(usernameTextbox).sendKeys("admin");
    assertHealed("waitForPresenceThenAct", "By.id");
  }

  // ---- 11. findElements (plural) is NOT healed --------------

  @Test
  void findElementsPluralNotHealed(WebDriver driver) {
    driver.get(PAGE);
    List<WebElement> matches = driver.findElements(By.cssSelector("#user-name"));  // broken -> empty, no heal
    assertTrue(matches.isEmpty(), "findElements returns [] for a broken locator, unhealed");
    assertNull(healOf("findElementsPluralNotHealed"));
  }
}
