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
    // GEMINI_THINKING=on keeps the model's default thinking budget; off (default) sends
    // reasoning_effort:none so a selector lookup returns in a few seconds instead of 15-30.
    boolean disableThinking = !"on".equalsIgnoreCase(String.valueOf(Env.get("GEMINI_THINKING")));
    return OpenAiCompatibleProvider.create(
        "gemini:" + model,
        baseUrl + "/chat/completions",
        Map.of("authorization", "Bearer " + apiKey),
        model,
        disableThinking);
  }
}
