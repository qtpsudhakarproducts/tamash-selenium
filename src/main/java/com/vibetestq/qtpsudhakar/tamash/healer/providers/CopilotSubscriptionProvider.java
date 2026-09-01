package com.vibetestq.qtpsudhakar.tamash.healer.providers;

import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;

import java.util.concurrent.CompletableFuture;
import com.vibetestq.qtpsudhakar.tamash.Env;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Self-healing backed by a personal GitHub Copilot subscription, via the official
 * {@code com.github:copilot-sdk-java} SDK (an optional dependency — add it yourself to use this
 * provider). The SDK drives the {@code copilot} CLI, which must be installed (>= 1.0.55) and
 * signed in, or an ambient {@code GITHUB_TOKEN} must be set. A single shared {@link CopilotClient}
 * is started lazily and stopped by a JVM shutdown hook; each call gets its own session.
 *
 * <p>SDK types appear only in method bodies so this class still loads when the optional dependency
 * is absent — a call then fails with {@code NoClassDefFoundError}, caught and reported like any
 * other provider failure.
 */
public final class CopilotSubscriptionProvider {
  private CopilotSubscriptionProvider() {}

  private static final long DEFAULT_TIMEOUT_MS = 45000;

  private static volatile Object clientHolder; // CopilotClient, lazily started
  private static final Object LOCK = new Object();

  public static HealProvider create() {
    String model = Env.get("COPILOT_SUBSCRIPTION_MODEL"); // null => CLI default
    String name = "copilot-subscription:" + (model != null ? model : "default");

    return new HealProvider() {
      @Override public String getName() { return name; }

      @Override
      public ProviderResult suggestSelector(SuggestSelectorInput input) {
        String out = send(Prompt.SYSTEM_PROMPT, Prompt.buildUserPrompt(input), timeout(input.getTimeoutMs()));
        if (out == null) return null;
        AiSuggestion s = Prompt.parseSuggestion(text(out));
        return s == null ? null : new ProviderResult(s, tokens(out));
      }

      @Override
      public ActionTacticResult suggestActionTactic(SuggestActionTacticInput input) {
        String out = send(Prompt.ACTION_RECOVERY_SYSTEM_PROMPT, Prompt.buildActionRecoveryUserPrompt(input),
            timeout(input.getTimeoutMs()));
        if (out == null) return null;
        ActionTactic t = Prompt.parseActionTacticSuggestion(text(out));
        return t == null ? null : new ActionTacticResult(t, tokens(out));
      }

      private long timeout(double requested) {
        return requested > 0 ? (long) requested + 15000 : DEFAULT_TIMEOUT_MS;
      }

      /** Returns "content\u0001outputTokens" or null. Kept as one string so the two parse paths
       *  above don't each need SDK types in their signatures. */
      private String send(String systemPrompt, String userPrompt, long timeoutMs) {
        try {
          CopilotClient client = client();
          SessionConfig cfg = new SessionConfig()
              .setAvailableTools(List.of())
              // No tools are available, so a permission request should never fire — but the SDK
              // mandates a handler. Deny anything that somehow does (never auto-approve in a
              // context that can run unattended in CI — matches the TS/Python providers' stance).
              .setOnPermissionRequest((req, inv) ->
                  CompletableFuture.completedFuture(PermissionRequestResult.reject("tamash-selenium: tools disabled")))
              .setSystemMessage(new SystemMessageConfig().setMode(SystemMessageMode.REPLACE).setContent(systemPrompt));
          if (model != null && !model.isEmpty()) {
            cfg.setModel(model);
          }
          CopilotSession session = client.createSession(cfg).get(30, TimeUnit.SECONDS);
          try {
            MessageOptions opts = new MessageOptions().setPrompt(userPrompt);
            AssistantMessageEvent ev = session.sendAndWait(opts, timeoutMs).get(timeoutMs + 5000, TimeUnit.MILLISECONDS);
            if (ev == null || ev.getData() == null || ev.getData().content() == null) {
              return null;
            }
            Long out = ev.getData().outputTokens();
            return ev.getData().content() + "\u0001" + (out != null ? out : "");
          } finally {
            try { session.close(); } catch (Exception ignored) { /* best effort */ }
          }
        } catch (NoClassDefFoundError e) {
          System.out.println("[self-healer] copilot-subscription: the com.github:copilot-sdk-java dependency isn't on "
              + "the classpath — add it to use HEALER_PROVIDER=copilot-subscription, or use openai/anthropic/gemini.");
          return null;
        } catch (Throwable e) {
          System.out.println("[self-healer] copilot-subscription: " + e.getMessage()
              + " — not authenticated? Install and sign in with the `copilot` CLI (>= 1.0.55), or set GITHUB_TOKEN.");
          return null;
        }
      }
    };
  }

  private static String text(String raw) {
    int nul = raw.indexOf('\u0001');
    return nul == -1 ? raw : raw.substring(0, nul);
  }

  private static TokenUsage tokens(String raw) {
    int nul = raw.indexOf('\u0001');
    if (nul == -1 || nul + 1 >= raw.length()) {
      return null;
    }
    try {
      int out = Integer.parseInt(raw.substring(nul + 1));
      return new TokenUsage(null, out, null);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static CopilotClient client() throws Exception {
    Object c = clientHolder;
    if (c == null) {
      synchronized (LOCK) {
        c = clientHolder;
        if (c == null) {
          CopilotClient created = new CopilotClient();
          created.start().get(60, TimeUnit.SECONDS);
          clientHolder = created;
          c = created;
          Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { created.forceStop().get(10, TimeUnit.SECONDS); } catch (Exception ignored) { /* best effort */ }
          }));
        }
      }
    }
    return (CopilotClient) c;
  }
}
