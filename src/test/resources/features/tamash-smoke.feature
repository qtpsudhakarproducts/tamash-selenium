Feature: tamash-selenium Cucumber smoke

  Scenario: heals a broken locator inside a step
    Given I am on the smoke page
    When I fill the username field via a broken locator
    Then the username field contains "Admin"
