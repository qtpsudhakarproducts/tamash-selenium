package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Shared {@link HealProvider} for any endpoint that speaks OpenAI's Chat Completions shape —
 * OpenAI itself and Gemini's OpenAI-compatible surface.
 */
public final class OpenAiCompatibleProvider {
  private OpenAiCompatibleProvider() {}

  private static final double DEFAULT_TIMEOUT_MS = 20000.0;

  public static HealProvider create(String name, String chatCompletionsUrl, Map<String, String> authHeaders,
                                    String model) {
    return create(name, chatCompletionsUrl, authHeaders, model, false);
  }

  /** @param disableThinking send {@code reasoning_effort: "low"} — Gemini 2.5+/3.x Flash think at a
   *   large budget by default and a 5k-token selector request then takes 15-30s; healing is
   *   structured extraction, not reasoning, so the minimum budget cuts each call to a few seconds.
   *   ({@code "none"} 400s on Gemini's OpenAI-compat surface; {@code "low"} is the floor.) Not sent
   *   for OpenAI, which 400s on the field for gpt-4o etc. */
  public static HealProvider create(String name, String chatCompletionsUrl, Map<String, String> authHeaders,
                                    String model, boolean disableThinking) {
    return new HealProvider() {
      @Override public String getName() { return name; }

      @Override
      public ProviderResult suggestSelector(SuggestSelectorInput input) {
        double t = timeout(input.getTimeoutMs());
        JSONObject body = baseBody()
            .put("messages", messages(Prompt.SYSTEM_PROMPT, Prompt.buildUserPrompt(input)));
        JSONObject payload = Http.postJson(name, chatCompletionsUrl, authHeaders, body, t);
        if (payload == null) return null;
        String content = content(payload);
        if (content == null) return null;
        AiSuggestion suggestion = Prompt.parseSuggestion(content);
        if (suggestion == null) return null;
        return new ProviderResult(suggestion, Prompt.extractOpenAiCompatibleUsage(payload));
      }

      @Override
      public ActionTacticResult suggestActionTactic(SuggestActionTacticInput input) {
        double t = timeout(input.getTimeoutMs());
        JSONObject body = baseBody()
            .put("messages", messages(Prompt.ACTION_RECOVERY_SYSTEM_PROMPT, Prompt.buildActionRecoveryUserPrompt(input)));
        JSONObject payload = Http.postJson(name + " action-recovery", chatCompletionsUrl, authHeaders, body, t);
        if (payload == null) return null;
        String content = content(payload);
        if (content == null) return null;
        ActionTactic tactic = Prompt.parseActionTacticSuggestion(content);
        if (tactic == null) return null;
        return new ActionTacticResult(tactic, Prompt.extractOpenAiCompatibleUsage(payload));
      }

      private JSONObject baseBody() {
        JSONObject b = new JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("response_format", new JSONObject().put("type", "json_object"));
        if (disableThinking) {
          b.put("reasoning_effort", "low");
        }
        return b;
      }

      private double timeout(double requested) {
        return requested > 0 ? requested : DEFAULT_TIMEOUT_MS;
      }
    };
  }

  private static JSONArray messages(String system, String userText) {
    return new JSONArray()
        .put(new JSONObject().put("role", "system").put("content", system))
        .put(new JSONObject().put("role", "user").put("content", userText));
  }

  private static String content(JSONObject payload) {
    JSONArray choices = payload.optJSONArray("choices");
    if (choices == null || choices.isEmpty()) return null;
    JSONObject message = choices.getJSONObject(0).optJSONObject("message");
    return message != null ? message.optString("content", null) : null;
  }
}
