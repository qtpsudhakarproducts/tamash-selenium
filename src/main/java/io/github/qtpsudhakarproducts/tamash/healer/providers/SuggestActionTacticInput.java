package io.github.qtpsudhakarproducts.tamash.healer.providers;

public final class SuggestActionTacticInput {
  private final String action;
  /** The error message from the failed replay attempt (not a selector problem). */
  private final String errorMessage;
  private final double timeoutMs;

  public SuggestActionTacticInput(String action, String errorMessage, double timeoutMs) {
    this.action = action;
    this.errorMessage = errorMessage;
    this.timeoutMs = timeoutMs;
  }

  public String getAction() { return action; }
  public String getErrorMessage() { return errorMessage; }
  public double getTimeoutMs() { return timeoutMs; }
}
