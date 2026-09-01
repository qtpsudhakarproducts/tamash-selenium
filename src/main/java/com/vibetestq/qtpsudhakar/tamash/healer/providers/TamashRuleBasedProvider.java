package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import com.vibetestq.qtpsudhakar.tamash.healer.DurableLocator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of src/healer/providers/tamash-rule-based.ts — a zero-dependency, zero-token provider: no
 * model call, no API key, no network. Resolves purely by text-matching the description against the
 * already-captured ARIA snapshot ({@link DurableLocator#findRuleBasedMatch}), reusing the exact
 * same never-guess discipline the AI-backed providers' prompt enforces. Narrower success envelope
 * than an AI provider (no action recovery — always declines).
 */
public final class TamashRuleBasedProvider {
  private TamashRuleBasedProvider() {}

  private static final Pattern TYPE_HINT_RE = Pattern.compile("^(.*)\\s\\(([^)]+)\\)$");

  record ParsedDescription(String phrase, String typeHint) {}

  static ParsedDescription parseDescriptionForMatch(String description) {
    if (description == null || description.isEmpty()) {
      return null;
    }
    String stripped = DurableLocator.stripGenericRoleSuffix(description);
    Matcher m = TYPE_HINT_RE.matcher(stripped);
    if (m.matches()) {
      String phrase = m.group(1).trim();
      return phrase.isEmpty() ? null : new ParsedDescription(phrase, m.group(2).trim());
    }
    return stripped.isEmpty() ? null : new ParsedDescription(stripped, null);
  }

  public static HealProvider create() {
    return new HealProvider() {
      @Override public String getName() { return "tamash"; }

      @Override
      public ProviderResult suggestSelector(SuggestSelectorInput input) {
        ParsedDescription parsed = parseDescriptionForMatch(input.getDescription());
        if (parsed == null) {
          return new ProviderResult(AiSuggestion.none(), null);
        }
        var nodes = DurableLocator.parseAriaAiTree(input.getAriaSnapshot());
        String expectedRole = parsed.typeHint() != null
            ? parsed.typeHint()
            : DurableLocator.inferRoleFromAction(input.getAction());
        AiSuggestion suggestion = DurableLocator.findRuleBasedMatch(nodes, parsed.phrase(), expectedRole);
        return new ProviderResult(suggestion, null);
      }

      @Override
      public ActionTacticResult suggestActionTactic(SuggestActionTacticInput input) {
        return new ActionTacticResult(ActionTactic.NONE, null);
      }
    };
  }
}
