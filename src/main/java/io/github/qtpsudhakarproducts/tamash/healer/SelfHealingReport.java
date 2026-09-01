package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.TokenUsage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Port of the TS {@code SelfHealingReport} (src/healer/index.ts). Mutable during a single
 *  {@code healActionFailure} call, then handed back read-only. */
public final class SelfHealingReport {
  public String action;
  public String kind;
  public String description;
  public String provider;
  public boolean healed;
  public String warning;
  public String reason;
  public String suggestedSelector;
  public TokenUsage tokenUsage;
  public String failureStage;
  public boolean usedActionRecovery;
  public String sourceLocation;
  public String testSelector;   // "com.foo.LoginTest#logsIn" — which test triggered this heal
  public String initialSelector;
  public Boolean needsReview;
  public String reviewNote;
  /** True when the healed locator was resolved inside an assertion call site (surfaced under
   *  {@code HEALER_ASSERTIONS=warn}; suppressed entirely under {@code strict}). */
  public boolean healedInAssertion;
  public final List<HealAttempt> attempts = new ArrayList<>();
  // The exact ARIA tree the AI reasoned over — held on the report so the step report can attach
  // it on an unrecovered failure (the way the TS/Python ports attach it to the test report).
  // Deliberately NOT in toJson()/the heal log: it's page content, not audit data.
  public transient String ariaSnapshotForReport;

  // ---- accessors (kept for source compatibility with the 0.1 API) ----------
  public String getAction() { return action; }
  public String getKind() { return kind; }
  public String getDescription() { return description; }
  public String getProvider() { return provider; }
  public boolean isHealed() { return healed; }
  public String getWarning() { return warning; }
  public String getReason() { return reason; }
  public String getSuggestedSelector() { return suggestedSelector; }
  public TokenUsage getTokenUsage() { return tokenUsage; }
  public String getFailureStage() { return failureStage; }
  public boolean isUsedActionRecovery() { return usedActionRecovery; }
  public String getSourceLocation() { return sourceLocation; }
  public String getInitialSelector() { return initialSelector; }
  public Boolean getNeedsReview() { return needsReview; }
  public String getReviewNote() { return reviewNote; }
  public List<HealAttempt> getAttempts() { return attempts; }

  public JSONObject toJson() {
    JSONObject o = new JSONObject();
    o.put("action", action);
    o.put("kind", kind);
    if (description != null) o.put("description", description);
    o.put("provider", provider);
    o.put("healed", healed);
    o.put("warning", warning);
    o.put("reason", reason);
    if (suggestedSelector != null) o.put("suggestedSelector", suggestedSelector);
    if (tokenUsage != null) {
      JSONObject tu = new JSONObject();
      if (tokenUsage.getInputTokens() != null) tu.put("inputTokens", tokenUsage.getInputTokens());
      if (tokenUsage.getOutputTokens() != null) tu.put("outputTokens", tokenUsage.getOutputTokens());
      if (tokenUsage.getTotalTokens() != null) tu.put("totalTokens", tokenUsage.getTotalTokens());
      o.put("tokenUsage", tu);
    }
    if (failureStage != null) o.put("failureStage", failureStage);
    if (healedInAssertion) o.put("healedInAssertion", true);
    o.put("usedActionRecovery", usedActionRecovery);
    if (sourceLocation != null) o.put("sourceLocation", sourceLocation);
    if (initialSelector != null) o.put("initialSelector", initialSelector);
    if (needsReview != null) o.put("needsReview", needsReview);
    if (reviewNote != null) o.put("reviewNote", reviewNote);
    JSONArray arr = new JSONArray();
    for (HealAttempt a : attempts) {
      arr.put(a.toJson());
    }
    o.put("attempts", arr);
    return o;
  }
}
