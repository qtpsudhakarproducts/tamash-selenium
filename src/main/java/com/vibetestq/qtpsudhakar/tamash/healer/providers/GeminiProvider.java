package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import com.vibetestq.qtpsudhakar.tamash.Env;

import java.util.Map;

/** Gemini's OpenAI-compatible surface — same request/response shape as OpenAI's Chat Completions. */
public final class GeminiProvider {
  private GeminiProvider() {}

  public static HealProvider create() {
    String apiKey = Env.get("GEMINI_API_KEY");
    String model = Env.get("GEMINI_MODEL");
    if (apiKey == null || apiKey.isEmpty() || model == null || model.isEmpty()) {
      return null;
    }
    String baseUrl = OpenAiProvider.envOrDefault(
        "GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai");
    return OpenAiCompatibleProvider.create(
        "gemini:" + model,
        baseUrl + "/chat/completions",
        Map.of("authorization", "Bearer " + apiKey),
        model);
  }
}
