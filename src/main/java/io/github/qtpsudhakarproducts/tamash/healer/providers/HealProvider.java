package io.github.qtpsudhakarproducts.tamash.healer.providers;

/**
 * Mirrors the TS {@code HealProvider} (src/healer/providers/types.ts). Every provider implements
 * every method unconditionally — callers key off {@link #supportsVision()}, not the presence of
 * {@link #suggestSelectorFromImage}. All three call methods return null on any failure (network
 * error, unparseable response, missing SDK/CLI, ...).
 */
public interface HealProvider {
  String getName();

  /** True when this provider's configured model is expected to accept image input; gates whether
   *  {@link #suggestSelectorFromImage} is actually called. */
  boolean supportsVision();

  /** Returns null if the call failed or the response couldn't be parsed into a suggestion. */
  ProviderResult suggestSelector(SuggestSelectorInput input);

  /** Screenshot-based fallback. Returns null on failure. */
  VisionProviderResult suggestSelectorFromImage(SuggestElementFromImageInput input);

  /** Picks one of a fixed set of retry tactics after a healed locator's replay still failed for a
   *  non-selector reason. Returns null on failure. */
  ActionTacticResult suggestActionTactic(SuggestActionTacticInput input);
}
