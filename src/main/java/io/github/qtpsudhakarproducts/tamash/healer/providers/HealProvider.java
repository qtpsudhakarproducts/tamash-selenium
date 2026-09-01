package io.github.qtpsudhakarproducts.tamash.healer.providers;

/**
 * A self-healing provider: given a description + a DOM accessibility snapshot it names the element,
 * and (opt-in) picks a retry tactic when a healed locator's replay still fails for a non-selector
 * reason. Both methods return null on any failure (network error, unparseable response, missing
 * SDK/CLI, ...).
 */
public interface HealProvider {
  String getName();

  /** Returns null if the call failed or the response couldn't be parsed into a suggestion. */
  ProviderResult suggestSelector(SuggestSelectorInput input);

  /** Picks one of a fixed set of retry tactics after a healed locator's replay still failed for a
   *  non-selector reason. Returns null on failure. */
  ActionTacticResult suggestActionTactic(SuggestActionTacticInput input);
}
