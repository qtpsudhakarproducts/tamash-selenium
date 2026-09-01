package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptTest {

  private static final String SNAP = "- generic [ref=e1]:\n  - textbox \"Username\" [ref=e2]";

  @Test
  void userPrompt_includesRawContextHints() {
    String p = Prompt.buildUserPrompt(new SuggestSelectorInput(
        "sendKeys", "User Name (textbox)", SNAP, 2000,
        "txtUserName", "By.cssSelector: #user-name", "LoginPage"));
    assertTrue(p.contains("Element description: User Name (textbox)"), p);
    assertTrue(p.contains("Locator variable/field name: txtUserName"), p);
    assertTrue(p.contains("Broken selector (no longer matches): By.cssSelector: #user-name"), p);
    assertTrue(p.contains("Defined in class: LoginPage"), p);
    assertTrue(p.contains("DOM snapshot:"), p);
  }

  @Test
  void userPrompt_omitsMissingAndRedundantHints() {
    // rawName equals the description -> not repeated; broken selector null -> omitted.
    String p = Prompt.buildUserPrompt(new SuggestSelectorInput(
        "click", "Login", SNAP, 2000, "Login", null, null));
    assertFalse(p.contains("Locator variable/field name"), p);
    assertFalse(p.contains("Broken selector"), p);
    assertFalse(p.contains("Defined in class"), p);
  }

  @Test
  void userPrompt_backCompatConstructorStillWorks() {
    String p = Prompt.buildUserPrompt(new SuggestSelectorInput("click", "Login", SNAP, 2000));
    assertTrue(p.contains("Failed action: click"), p);
    assertFalse(p.contains("Locator variable/field name"), p);
  }

  @Test
  void parseSuggestion_seleniumStrategies() {
    assertEquals("id", Prompt.parseSuggestion("{\"strategy\":\"id\",\"id\":\"username\"}").getStrategy());
    assertEquals("xpath", Prompt.parseSuggestion("{\"strategy\":\"xpath\",\"xpath\":\"//a\"}").getStrategy());
    assertEquals("ref", Prompt.parseSuggestion("{\"strategy\":\"ref\",\"ref\":\"e7\"}").getStrategy());
    assertTrue(Prompt.parseSuggestion("{\"strategy\":\"none\"}").isNone());
    assertNull(Prompt.parseSuggestion("not json"));
  }
}
