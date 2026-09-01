package com.vibetestq.qtpsudhakar.tamash.healer.providers;

public final class ProviderResult {
  private final AiSuggestion suggestion;
  private final TokenUsage usage;

  public ProviderResult(AiSuggestion suggestion, TokenUsage usage) {
    this.suggestion = suggestion;
    this.usage = usage;
  }

  public AiSuggestion getSuggestion() { return suggestion; }
  public TokenUsage getUsage() { return usage; }
}
