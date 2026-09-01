package com.vibetestq.qtpsudhakar.tamash.healer.providers;

public final class SuggestSelectorInput {
  private final String action;
  private final String description;
  private final String ariaSnapshot;
  private final double timeoutMs;

  // Extra raw context for the AI finder — the model can expand a terse name (txtUserName) itself,
  // and read intent from the broken selector, better than the deterministic decoder could.
  private final String rawName;
  private final String brokenSelector;
  private final String contextClass;

  public SuggestSelectorInput(String action, String description, String ariaSnapshot, double timeoutMs) {
    this(action, description, ariaSnapshot, timeoutMs, null, null, null);
  }

  public SuggestSelectorInput(String action, String description, String ariaSnapshot, double timeoutMs,
                              String rawName, String brokenSelector, String contextClass) {
    this.action = action;
    this.description = description;
    this.ariaSnapshot = ariaSnapshot;
    this.timeoutMs = timeoutMs;
    this.rawName = rawName;
    this.brokenSelector = brokenSelector;
    this.contextClass = contextClass;
  }

  public String getAction() { return action; }
  public String getDescription() { return description; }
  public String getAriaSnapshot() { return ariaSnapshot; }
  public double getTimeoutMs() { return timeoutMs; }
  public String getRawName() { return rawName; }
  public String getBrokenSelector() { return brokenSelector; }
  public String getContextClass() { return contextClass; }
}
