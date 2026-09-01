package io.github.qtpsudhakarproducts.tamash.healer.providers;

import io.github.qtpsudhakarproducts.tamash.Env;

import java.util.Map;

public final class OpenAiProvider {
  private OpenAiProvider() {}

  public static HealProvider create() {
    String apiKey = Env.get("OPENAI_API_KEY");
    String model = Env.get("OPENAI_MODEL");
    if (apiKey == null || apiKey.isEmpty() || model == null || model.isEmpty()) {
      return null;
    }
    String baseUrl = envOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
    return OpenAiCompatibleProvider.create(
        "openai:" + model,
        baseUrl + "/chat/completions",
        Map.of("authorization", "Bearer " + apiKey),
        model);
  }

  /** Trims trailing slashes; falls back to {@code fallback} when unset/empty. */
  public static String envOrDefault(String key, String fallback) {
    String value = Env.get(key);
    return (value == null || value.isEmpty()) ? fallback : value.replaceAll("/+$", "");
  }
}
