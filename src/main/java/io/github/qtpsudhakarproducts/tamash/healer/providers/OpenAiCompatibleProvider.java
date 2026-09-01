package io.github.qtpsudhakarproducts.tamash.healer.providers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Shared {@link HealProvider} for any endpoint that speaks OpenAI's Chat Completions shape —
 * OpenAI itself and Gemini's OpenAI-compatible surface.
 */
public final class OpenAiCompatibleProvider {
  private OpenAiCompatibleProvider() {}

  private static final double DEFAULT_TIMEOUT_MS = 15000.0;

  public static HealProvider create(String name, String chatCompletionsUrl, Map<String, String> authHeaders,
                                    String model) {
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
        return new JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("response_format", new JSONObject().put("type", "json_object"));
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
