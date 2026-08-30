package io.github.qtpsudhakarproducts.tamash.healer.providers;

public final class SuggestElementFromImageInput {
  private final String action;
  private final String description;
  /** Raw PNG bytes, base64-encoded, no {@code data:} URI prefix. */
  private final String imageBase64;
  private final double timeoutMs;

  public SuggestElementFromImageInput(String action, String description, String imageBase64, double timeoutMs) {
    this.action = action;
    this.description = description;
    this.imageBase64 = imageBase64;
    this.timeoutMs = timeoutMs;
  }

  public String getAction() { return action; }
  public String getDescription() { return description; }
  public String getImageBase64() { return imageBase64; }
  public double getTimeoutMs() { return timeoutMs; }
}
