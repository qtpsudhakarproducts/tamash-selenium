package io.github.qtpsudhakarproducts.tamash.healer.providers;

public final class VisionProviderResult {
  private final VisionPoint point;
  private final TokenUsage usage;

  public VisionProviderResult(VisionPoint point, TokenUsage usage) {
    this.point = point;
    this.usage = usage;
  }

  public VisionPoint getPoint() { return point; }
  public TokenUsage getUsage() { return usage; }
}
