package com.vibetestq.qtpsudhakar.tamash.healer.providers;

public final class TokenUsage {
  private final Integer inputTokens;
  private final Integer outputTokens;
  private final Integer totalTokens;

  public TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.totalTokens = totalTokens;
  }

  public Integer getInputTokens() { return inputTokens; }
  public Integer getOutputTokens() { return outputTokens; }
  public Integer getTotalTokens() { return totalTokens; }

  private static Integer sumOptional(Integer a, Integer b) {
    if (a == null && b == null) return null;
    return (a == null ? 0 : a) + (b == null ? 0 : b);
  }

  /** Combines usage from two attempts on the same failure (e.g. text + action-recovery) — both are real
   *  spend, so a failed heal that tried both still reports the full cost. Null-safe on either arg. */
  public static TokenUsage plus(TokenUsage a, TokenUsage b) {
    if (a == null) return b;
    if (b == null) return a;
    return new TokenUsage(
        sumOptional(a.inputTokens, b.inputTokens),
        sumOptional(a.outputTokens, b.outputTokens),
        sumOptional(a.totalTokens, b.totalTokens));
  }

  public String format() {
    StringBuilder parts = new StringBuilder();
    if (inputTokens != null) {
      parts.append(inputTokens).append(" input");
    }
    if (outputTokens != null) {
      if (parts.length() > 0) parts.append(" + ");
      parts.append(outputTokens).append(" output");
    }
    String breakdown = parts.length() > 0 ? " (" + parts + ")" : "";
    return (totalTokens != null ? totalTokens + " tokens" : "tokens") + breakdown;
  }
}
