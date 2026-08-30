package io.github.qtpsudhakarproducts.tamash.healer.providers;

public final class ActionTacticResult {
  private final ActionTactic tactic;
  private final TokenUsage usage;

  public ActionTacticResult(ActionTactic tactic, TokenUsage usage) {
    this.tactic = tactic;
    this.usage = usage;
  }

  public ActionTactic getTactic() { return tactic; }
  public TokenUsage getUsage() { return usage; }
}
