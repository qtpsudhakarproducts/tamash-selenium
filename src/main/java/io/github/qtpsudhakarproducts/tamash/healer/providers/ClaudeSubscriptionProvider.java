package io.github.qtpsudhakarproducts.tamash.healer.providers;

import io.github.qtpsudhakarproducts.tamash.Env;

/**
 * Self-healing backed by a personal Claude subscription rather than a pay-per-token API key.
 *
 * <p>There is no official Anthropic <em>Agent</em> SDK for Java (the {@code com.anthropic:anthropic-java}
 * SDK is API-key REST only), so this uses that same SDK's Messages endpoint authenticated with a
 * Claude Code OAuth token ({@code CLAUDE_CODE_OAUTH_TOKEN}, from {@code claude setup-token}) plus
 * the {@code anthropic-beta: oauth-2025-04-20} header and the Claude Code identity system block —
 * the wire contract the agent SDK ultimately speaks for a single-shot query. Always constructs;
 * auth is only verifiable per call.
 */
public final class ClaudeSubscriptionProvider {
  private ClaudeSubscriptionProvider() {}

  public static HealProvider create() {
    String model = envOrDefault("CLAUDE_SUBSCRIPTION_MODEL", "claude-haiku-4-5");
    String token = Env.get("CLAUDE_CODE_OAUTH_TOKEN");
    String name = "claude-subscription:" + model;

    if (token == null || token.isEmpty()) {
      return unavailable(name, model);
    }
    return AnthropicSdkProvider.create(name, null, token, model);
  }

  private static String envOrDefault(String key, String fallback) {
    String v = Env.get(key);
    return (v == null || v.isEmpty()) ? fallback : v;
  }

  private static HealProvider unavailable(String name, String model) {
    return new HealProvider() {
      @Override public String getName() { return name; }
      private <T> T warn() {
        System.out.println("[self-healer] claude-subscription: CLAUDE_CODE_OAUTH_TOKEN is not set — "
            + "run `claude setup-token`, or use HEALER_PROVIDER=anthropic + ANTHROPIC_API_KEY instead.");
        return null;
      }
      @Override public ProviderResult suggestSelector(SuggestSelectorInput i) { return warn(); }
      @Override public ActionTacticResult suggestActionTactic(SuggestActionTacticInput i) { return warn(); }
    };
  }
}
