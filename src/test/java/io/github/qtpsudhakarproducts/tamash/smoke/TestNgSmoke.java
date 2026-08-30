package io.github.qtpsudhakarproducts.tamash.smoke;

import io.github.qtpsudhakarproducts.tamash.testng.TamashSeleniumTestNgTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

/** Driven programmatically by {@code FrameworkIntegrationTest} (not by the default surefire run). */
public class TestNgSmoke extends TamashSeleniumTestNgTest {

  @Test
  public void healsABrokenLocator() {
    driver.get(SmokePage.URL);
    // Wrong id — the real field is #username; "usernameInput" decodes to "Username (textbox)".
    WebElement usernameInput = driver.findElement(By.cssSelector("#wrong-username"));
    usernameInput.sendKeys("Admin");
  }
}
