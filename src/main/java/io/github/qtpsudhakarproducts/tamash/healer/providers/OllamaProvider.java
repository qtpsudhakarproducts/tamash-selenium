package io.github.qtpsudhakarproducts.tamash.healer.providers;

import io.github.qtpsudhakarproducts.tamash.Env;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Ollama's native {@code /api/chat} endpoint (the OpenAI-compatible path 410s on Ollama Cloud).
 *  Backs both {@code ollama} (Ollama Cloud, {@code OLLAMA_API_KEY} required) and
 *  {@code ollama-local} (a self-hosted / internal {@code ollama serve}, key optional). */
public final class OllamaProvider {
  private OllamaProvider() {}

  private static final double DEFAULT_TIMEOUT_MS = 15000.0;

  /** Ollama Cloud. Requires {@code OLLAMA_API_KEY} + {@code OLLAMA_MODEL}. */
  public static HealProvider create() {
    String apiKey = Env.get("OLLAMA_API_KEY");
    String model = Env.get("OLLAMA_MODEL");
    if (apiKey == null || apiKey.isEmpty() || model == null || model.isEmpty()) {
      return null;
    }
    String baseUrl = OpenAiProvider.envOrDefault("OLLAMA_BASE_URL", "https://ollama.com");
    Map<String, String> headers = Map.of("authorization", "Bearer " + apiKey);
    return build("ollama:" + model, baseUrl.replaceAll("/+$", "") + "/api/chat", headers, model);
  }

  /** A self-hosted / internal Ollama server. A deliberately separate provider from {@code ollama},
   *  not a flag on it: a bare {@code ollama serve} has no auth at all, so {@code OLLAMA_LOCAL_API_KEY}
   *  is optional — set it only when the deployment sits behind a reverse proxy / gateway that wants
   *  a bearer token. {@code OLLAMA_LOCAL_BASE_URL} defaults to {@code http://localhost:11434}. */
  public static HealProvider createLocal() {
    String model = Env.get("OLLAMA_LOCAL_MODEL");
    if (model == null || model.isEmpty()) {
      return null;
    }
    String baseUrl = OpenAiProvider.envOrDefault("OLLAMA_LOCAL_BASE_URL", "http://localhost:11434")
        .replaceAll("/+$", "");
    Map<String, String> headers = new HashMap<>();
    String apiKey = Env.get("OLLAMA_LOCAL_API_KEY");
    if (apiKey != null && !apiKey.isEmpty()) {
      headers.put("authorization", "Bearer " + apiKey);
    }
    return build("ollama-local:" + model, baseUrl + "/api/chat", Map.copyOf(headers), model);
  }

  private static HealProvider build(String name, String url, Map<String, String> headers, String model) {
    boolean supportsVision = VisionModels.isVisionCapableModel("ollama", model);

    return new HealProvider() {
      @Override public String getName() { return name; }
      @Override public boolean supportsVision() { return supportsVision; }

      @Override
      public ProviderResult suggestSelector(SuggestSelectorInput input) {
        JSONObject payload = post(Prompt.SYSTEM_PROMPT, Prompt.buildUserPrompt(input), null, timeout(input.getTimeoutMs()), "");
        if (payload == null) return null;
        String content = content(payload);
        if (content == null) return null;
        AiSuggestion s = Prompt.parseSuggestion(content);
        return s == null ? null : new ProviderResult(s, usage(payload));
      }

      @Override
      public VisionProviderResult suggestSelectorFromImage(SuggestElementFromImageInput input) {
        JSONObject payload = post(Prompt.VISION_SYSTEM_PROMPT, Prompt.buildVisionUserPrompt(input),
            input.getImageBase64(), timeout(input.getTimeoutMs()), " vision");
        if (payload == null) return null;
        String content = content(payload);
        if (content == null) return null;
        VisionPoint p = Prompt.parseVisionSuggestion(content);
        return p == null ? null : new VisionProviderResult(p, usage(payload));
      }

      @Override
      public ActionTacticResult suggestActionTactic(SuggestActionTacticInput input) {
        JSONObject payload = post(Prompt.ACTION_RECOVERY_SYSTEM_PROMPT, Prompt.buildActionRecoveryUserPrompt(input),
            null, timeout(input.getTimeoutMs()), " action-recovery");
        if (payload == null) return null;
        String content = content(payload);
        if (content == null) return null;
        ActionTactic tactic = Prompt.parseActionTacticSuggestion(content);
        return tactic == null ? null : new ActionTacticResult(tactic, usage(payload));
      }

      private JSONObject post(String system, String userText, String imageBase64, double t, String labelSuffix) {
        JSONObject userMsg = new JSONObject().put("role", "user").put("content", userText);
        if (imageBase64 != null) {
          userMsg.put("images", new JSONArray().put(imageBase64));
        }
        JSONObject body = new JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("format", "json")
            .put("options", new JSONObject().put("temperature", 0))
            .put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(userMsg));
        return Http.postJson(name + labelSuffix, url, headers, body, t);
      }

      private double timeout(double requested) {
        return requested > 0 ? requested : DEFAULT_TIMEOUT_MS;
      }
    };
  }

  private static String content(JSONObject payload) {
    JSONObject message = payload.optJSONObject("message");
    return message != null ? message.optString("content", null) : null;
  }

  private static TokenUsage usage(JSONObject payload) {
    Integer in = Prompt.optIntOrNull(payload, "prompt_eval_count");
    Integer out = Prompt.optIntOrNull(payload, "eval_count");
    Integer total = (in != null && out != null) ? in + out : null;
    return new TokenUsage(in, out, total);
  }
}
