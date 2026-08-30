package io.github.qtpsudhakarproducts.tamash.healer.providers;

import io.github.qtpsudhakarproducts.tamash.Env;

/**
 * HEALER_PROVIDER picks which provider backs self-healing. Each API-key provider gracefully
 * returns null (disabling healing for that action) if its own env vars aren't set. The two
 * subscription providers and {@code tamash} always construct — auth/availability is only
 * verifiable per call (or, for {@code tamash}, there's nothing to verify).
 *
 * <p><b>Default:</b> when {@code HEALER_PROVIDER} is unset the rule-based {@code tamash} provider
 * is used — plug-and-play healing with no key, no network, no tokens. {@code tamash} never
 * guesses, so a failed heal just fails like it would without the package. Set {@code HEALER_PROVIDER}
 * + a key for AI-backed healing; {@code HEALER_ENABLED=false} turns healing off entirely.
 */
public final class ProviderFactory {
  private ProviderFactory() {}

  private static volatile HealProvider cachedProvider;
  private static volatile boolean cachePopulated = false;

  public static synchronized HealProvider getHealProvider() {
    if (cachePopulated) {
      return cachedProvider;
    }
    String providerName = Env.get("HEALER_PROVIDER");
    cachedProvider = (providerName == null || providerName.isBlank())
        ? TamashRuleBasedProvider.create()   // plug-and-play default: rule-based, no key, no network
        : switch (providerName) {
            case "ollama" -> OllamaProvider.create();
            case "ollama-local" -> OllamaProvider.createLocal();
            case "openai" -> OpenAiProvider.create();
            case "anthropic" -> AnthropicProvider.create();
            case "gemini" -> GeminiProvider.create();
            case "claude-subscription" -> ClaudeSubscriptionProvider.create();
            case "copilot-subscription" -> CopilotSubscriptionProvider.create();
            case "tamash" -> TamashRuleBasedProvider.create();
            default -> null;
          };
    cachePopulated = true;
    return cachedProvider;
  }

  /** Mainly for tests / the doctor CLI re-checking within the same process after env changes. */
  public static synchronized void resetCache() {
    cachedProvider = null;
    cachePopulated = false;
  }
}
