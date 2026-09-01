package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptTest {

  private static final String SNAP = "- generic [ref=e1]:\n  - textbox \"Username\" [ref=e2]";

  // ---- user prompt --------------------------------------------------------

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
  void userPrompt_truncatesAnOversizedSnapshot() {
    String huge = "- generic [ref=e1]:\n" + "  - text: filler\n".repeat(4000); // well over 16000 chars
    String p = Prompt.buildUserPrompt(new SuggestSelectorInput("click", "X", huge, 2000));
    assertTrue(p.contains("... (truncated)"), "oversized snapshot should be truncated");
    assertTrue(p.length() < huge.length(), "prompt shorter than raw snapshot");
  }

  // ---- system prompts ---------------------------------------------------

  @Test
  void systemPrompts_areSeleniumFlavoured() {
    assertTrue(Prompt.SYSTEM_PROMPT.contains("Selenium self-healing assistant"), "selector prompt");
    assertFalse(Prompt.SYSTEM_PROMPT.toLowerCase().contains("playwright"), "no playwright leakage");
    assertTrue(Prompt.ACTION_RECOVERY_SYSTEM_PROMPT.contains("Selenium"), "action recovery prompt");
    assertTrue(Prompt.SYSTEM_PROMPT.contains("\"strategy\":\"ref\""), "ref is the primary strategy");
  }

  // ---- parseSuggestion: every strategy shape ---------------------------

  @Test
  void parseSuggestion_ref_withNearbyHints() {
    AiSuggestion s = Prompt.parseSuggestion(
        "{\"strategy\":\"ref\",\"ref\":\"e7\",\"nearbyText\":\"Email\",\"nearbyRole\":\"text\"}");
    assertEquals("ref", s.getStrategy());
    assertEquals("e7", s.getRef());
    assertEquals("Email", s.getNearbyText());
    assertEquals("text", s.getNearbyRole());
    assertFalse(s.isPersistable(), "a bare ref is not persistable");
  }

  @Test
  void parseSuggestion_id_name_css_xpath_text() {
    assertEquals("username", Prompt.parseSuggestion("{\"strategy\":\"id\",\"id\":\"username\"}").getId());
    assertEquals("firstName", Prompt.parseSuggestion("{\"strategy\":\"name\",\"name\":\"firstName\"}").getNameAttr());
    assertEquals("button.go", Prompt.parseSuggestion("{\"strategy\":\"css\",\"css\":\"button.go\"}").getCss());
    assertEquals("//a[1]", Prompt.parseSuggestion("{\"strategy\":\"xpath\",\"xpath\":\"//a[1]\"}").getXpath());
    assertEquals("Sign in", Prompt.parseSuggestion("{\"strategy\":\"text\",\"text\":\"Sign in\"}").getText());
  }

  @Test
  void parseSuggestion_near_needsBothAnchorAndRole() {
    AiSuggestion ok = Prompt.parseSuggestion("{\"strategy\":\"near\",\"anchorText\":\"Email\",\"role\":\"input\"}");
    assertEquals("near", ok.getStrategy());
    assertEquals("Email", ok.getAnchorText());
    assertNull(Prompt.parseSuggestion("{\"strategy\":\"near\",\"anchorText\":\"Email\"}"), "missing role -> null");
  }

  @Test
  void parseSuggestion_none() {
    assertTrue(Prompt.parseSuggestion("{\"strategy\":\"none\"}").isNone());
  }

  @Test
  void parseSuggestion_roleIsFoldedIntoCssOrXpath() {
    assertEquals("[role='dialog']", Prompt.parseSuggestion("{\"strategy\":\"role\",\"role\":\"dialog\"}").getCss());
    AiSuggestion named = Prompt.parseSuggestion("{\"strategy\":\"role\",\"role\":\"button\",\"name\":\"OK\"}");
    assertEquals("xpath", named.getStrategy());
    assertTrue(named.getXpath().contains("'OK'"), named.getXpath());
  }

  @Test
  void parseSuggestion_toleratesMarkdownFencedJson() {
    AiSuggestion s = Prompt.parseSuggestion("```json\n{\"strategy\":\"id\",\"id\":\"go\"}\n```");
    assertEquals("go", s.getId());
  }

  @Test
  void parseSuggestion_rejectsGarbageAndMissingFields() {
    assertNull(Prompt.parseSuggestion("not json at all"));
    assertNull(Prompt.parseSuggestion("{\"strategy\":\"id\"}"), "id strategy with no id");
    assertNull(Prompt.parseSuggestion("{\"nope\":1}"), "no strategy key");
    assertNull(Prompt.parseSuggestion("{\"strategy\":\"unheard-of\"}"));
  }

  // ---- action tactic parsing -----------------------------------------

  @Test
  void parseActionTactic_everyWireValue() {
    assertEquals(ActionTactic.SCROLL, Prompt.parseActionTacticSuggestion("{\"tactic\":\"scroll\"}"));
    assertEquals(ActionTactic.FORCE, Prompt.parseActionTacticSuggestion("{\"tactic\":\"force\"}"));
    assertEquals(ActionTactic.WAIT, Prompt.parseActionTacticSuggestion("{\"tactic\":\"wait\"}"));
    assertEquals(ActionTactic.DISPATCH, Prompt.parseActionTacticSuggestion("{\"tactic\":\"dispatch\"}"));
    assertEquals(ActionTactic.NONE, Prompt.parseActionTacticSuggestion("{\"tactic\":\"none\"}"));
    assertNull(Prompt.parseActionTacticSuggestion("{\"tactic\":\"teleport\"}"));
    assertNull(Prompt.parseActionTacticSuggestion("{}"));
  }

  // ---- usage extraction --------------------------------------------

  @Test
  void extractUsage_openAiShape() {
    JSONObject payload = new JSONObject().put("usage", new JSONObject()
        .put("prompt_tokens", 100).put("completion_tokens", 20).put("total_tokens", 120));
    TokenUsage u = Prompt.extractOpenAiCompatibleUsage(payload);
    assertEquals(Integer.valueOf(100), u.getInputTokens());
    assertEquals(Integer.valueOf(20), u.getOutputTokens());
    assertEquals(Integer.valueOf(120), u.getTotalTokens());
  }

  @Test
  void extractUsage_absentUsageIsNull() {
    assertNull(Prompt.extractOpenAiCompatibleUsage(new JSONObject()));
  }
}
