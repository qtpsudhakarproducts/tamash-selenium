package com.vibetestq.qtpsudhakar.tamash.smoke;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static com.vibetestq.qtpsudhakar.tamash.cucumber.TamashSeleniumScenario.driver;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Step glue for {@code tamash-smoke.feature} — driven by {@code FrameworkIntegrationTest}. */
public class TamashSmokeSteps {

  @Given("I am on the smoke page")
  public void onSmokePage() {
    driver().get(SmokePage.URL);
  }

  @When("I fill the username field via a broken locator")
  public void fillBroken() {
    WebElement usernameInput = driver().findElement(By.cssSelector("#wrong-username"));
    usernameInput.sendKeys("Admin");
  }

  @Then("the username field contains {string}")
  public void usernameContains(String expected) {
    assertEquals(expected, driver().findElement(By.id("username")).getAttribute("value"));
  }
}
