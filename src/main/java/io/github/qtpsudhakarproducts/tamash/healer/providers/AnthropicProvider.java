package io.github.qtpsudhakarproducts.tamash.healer.providers;

import io.github.qtpsudhakarproducts.tamash.Env;

public final class AnthropicProvider {
  private AnthropicProvider() {}

  public static HealProvider create() {
    String apiKey = Env.get("ANTHROPIC_API_KEY");
    String model = Env.get("ANTHROPIC_MODEL");
    if (apiKey == null || apiKey.isEmpty() || model == null || model.isEmpty()) {
      return null;
    }
    return AnthropicSdkProvider.create("anthropic:" + model, apiKey, null, model);
  }
}
