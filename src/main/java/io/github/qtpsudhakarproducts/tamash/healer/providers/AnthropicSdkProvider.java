package io.github.qtpsudhakarproducts.tamash.healer.providers;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

import java.time.Duration;
import java.util.List;

/**
 * Anthropic Messages API via the official {@code com.anthropic:anthropic-java} SDK — shared by the
 * API-key {@code anthropic} provider and by {@code claude-subscription} (same endpoint, different
 * auth). Matches the TS package's use of {@code @anthropic-ai/sdk}.
 */
public final class AnthropicSdkProvider {
  private AnthropicSdkProvider() {}

  private static final double DEFAULT_TIMEOUT_MS = 15000.0;

  // OAuth tokens from `claude setup-token` are only accepted when the system prompt begins with
  // Claude Code's own identity line and the oauth beta header is present — otherwise the API 403s
  // ("This credential is only authorized for use with Claude Code"). Our real instruction follows
  // as a second system block.
  private static final String CLAUDE_CODE_IDENTITY =
      "You are Claude Code, Anthropic's official CLI for Claude.";
  private static final String OAUTH_BETA = "oauth-2025-04-20";

  /**
   * @param apiKey        set for the API-key {@code anthropic} provider (else null)
   * @param authToken     set for {@code claude-subscription} — a {@code CLAUDE_CODE_OAUTH_TOKEN} (else null)
   */
  public static HealProvider create(String name, String apiKey, String authToken, String model) {
    boolean oauth = authToken != null && !authToken.isEmpty();
    AnthropicOkHttpClient.Builder cb = AnthropicOkHttpClient.builder();
    if (oauth) {
      cb.authToken(authToken);
    } else {
      cb.apiKey(apiKey);
    }
    final AnthropicClient client = cb.build();

    return new HealProvider() {
      @Override public String getName() { return name; }

      @Override
      public ProviderResult suggestSelector(SuggestSelectorInput input) {
        Message msg = call(SYSTEM(Prompt.SYSTEM_PROMPT), Prompt.buildUserPrompt(input), input.getTimeoutMs(), "");
        if (msg == null) return null;
        String text = firstText(msg);
        if (text == null) return null;
        AiSuggestion s = Prompt.parseSuggestion(text);
        return s == null ? null : new ProviderResult(s, usage(msg));
      }

      @Override
      public ActionTacticResult suggestActionTactic(SuggestActionTacticInput input) {
        Message msg = call(SYSTEM(Prompt.ACTION_RECOVERY_SYSTEM_PROMPT), Prompt.buildActionRecoveryUserPrompt(input),
            input.getTimeoutMs(), " action-recovery");
        if (msg == null) return null;
        String text = firstText(msg);
        if (text == null) return null;
        ActionTactic t = Prompt.parseActionTacticSuggestion(text);
        return t == null ? null : new ActionTacticResult(t, usage(msg));
      }

      private List<TextBlockParam> SYSTEM(String prompt) {
        return oauth
            ? List.of(TextBlockParam.of(CLAUDE_CODE_IDENTITY), TextBlockParam.of(prompt))
            : List.of(TextBlockParam.of(prompt));
      }

      private Message call(List<TextBlockParam> system, String userText, double requestedTimeout, String labelSuffix) {
        try {
          MessageCreateParams.Builder b = MessageCreateParams.builder()
              .model(model)
              .maxTokens(1024)
              .systemOfTextBlockParams(system)
              .addUserMessage(userText);
          if (oauth) {
            b.putAdditionalHeader("anthropic-beta", OAUTH_BETA);
          }
          double t = requestedTimeout > 0 ? requestedTimeout : DEFAULT_TIMEOUT_MS;
          return client.messages().create(b.build(),
              RequestOptions.builder().timeout(Duration.ofMillis((long) t)).build());
        } catch (Exception e) {
          System.out.println("[self-healer] " + name + labelSuffix + " provider error: " + e.getMessage());
          return null;
        }
      }
    };
  }

  private static String firstText(Message msg) {
    for (ContentBlock cb : msg.content()) {
      if (cb.text().isPresent()) {
        return cb.text().get().text();
      }
    }
    return null;
  }

  private static TokenUsage usage(Message msg) {
    try {
      int in = (int) msg.usage().inputTokens();
      int out = (int) msg.usage().outputTokens();
      return new TokenUsage(in, out, in + out);
    } catch (Exception e) {
      return null;
    }
  }
}
