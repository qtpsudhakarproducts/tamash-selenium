package io.github.qtpsudhakarproducts.tamash.healer.providers;

/**
 * The AI's screenshot-based answer. When {@link #isFound()}, {@code x}/{@code y} are normalized
 * 0–1000 (fraction of image width/height × 1000) marking the CENTER of the matched element —
 * normalized so the caller never needs the real PNG dimensions or device scale factor.
 */
public final class VisionPoint {
  private final boolean found;
  private final double x;
  private final double y;

  private VisionPoint(boolean found, double x, double y) {
    this.found = found;
    this.x = x;
    this.y = y;
  }

  public static VisionPoint notFound() {
    return new VisionPoint(false, 0, 0);
  }

  public static VisionPoint at(double x, double y) {
    return new VisionPoint(true, x, y);
  }

  public boolean isFound() { return found; }
  public double getX() { return x; }
  public double getY() { return y; }
}
