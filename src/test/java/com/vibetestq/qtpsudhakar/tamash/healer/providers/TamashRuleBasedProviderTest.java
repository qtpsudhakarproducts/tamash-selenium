package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of pw's tamashRuleBasedProvider.test.js — the zero-AI provider as a whole: description
 * parsing, the decline paths, and the always-decline action-tactic method.
 */
class TamashRuleBasedProviderTest {

  private static final String SNAP = """
      - generic [ref=e1] [box=8,21,1264,139]:
        - heading "Account" [ref=e2] [box=8,21,1264,37]
        - text: Username
        - textbox "Username" [ref=e5] [box=292,80,177,21]
        - button "Save" [ref=e6] [box=8,119,55,21]
        - button "Delete" [ref=e10] [box=8,140,55,21]
        - button "Delete" [ref=e11] [box=63,140,55,21]
      """;

  private static ProviderResult heal(String description, String action) {
    return TamashRuleBasedProvider.create().suggestSelector(
        new SuggestSelectorInput(action, description, SNAP, 2000));
  }

  // ---- parseDescriptionForMatch ---------------------------------------

  @Test
  void parseDescription_splitsPhraseAndTypeHint() {
    var p = TamashRuleBasedProvider.parseDescriptionForMatch("First Name (textbox)");
    assertEquals("First Name", p.phrase());
    assertEquals("textbox", p.typeHint());
  }

  @Test
  void parseDescription_barePhraseHasNoHint() {
    var p = TamashRuleBasedProvider.parseDescriptionForMatch("Save");
    assertEquals("Save", p.phrase());
    assertNull(p.typeHint());
  }

  @Test
  void parseDescription_emptyOrNullIsNull() {
    assertNull(TamashRuleBasedProvider.parseDescriptionForMatch(null));
    assertNull(TamashRuleBasedProvider.parseDescriptionForMatch(""));
  }

  // ---- suggestSelector -------------------------------------------------

  @Test
  void suggestSelector_resolvesANamedField() {
    ProviderResult r = heal("Username (textbox)", "sendKeys");
    assertEquals("ref", r.getSuggestion().getStrategy());
    assertEquals("e5", r.getSuggestion().getRef());
    assertNull(r.getUsage(), "rule-based provider never reports token usage");
  }

  @Test
  void suggestSelector_declinesOnGenuineAmbiguity() {
    ProviderResult r = heal("Delete (button)", "click");
    assertTrue(r.getSuggestion().isNone(), "two identical 'Delete' buttons -> decline");
  }

  @Test
  void suggestSelector_declinesWhenDescriptionUnparseable() {
    ProviderResult r = heal(null, "click");
    assertTrue(r.getSuggestion().isNone());
  }

  @Test
  void suggestSelector_declinesWhenNothingMatches() {
    ProviderResult r = heal("Nonexistent Widget (textbox)", "sendKeys");
    assertTrue(r.getSuggestion().isNone());
  }

  // ---- action tactic -------------------------------------------------

  @Test
  void suggestActionTactic_alwaysDeclines() {
    ActionTacticResult r = TamashRuleBasedProvider.create().suggestActionTactic(
        new SuggestActionTacticInput("click", "ElementClickInterceptedException", 2000));
    assertEquals(ActionTactic.NONE, r.getTactic());
    assertNull(r.getUsage());
  }

  @Test
  void providerName_isTamash() {
    assertEquals("tamash", TamashRuleBasedProvider.create().getName());
  }
}
