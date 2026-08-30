package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.VisionPoint;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Port of src/healer/vision.ts — screenshot capture and translating the AI's normalized point
 * back to a real DOM element (tagged with a temporary attribute).
 *
 * <p>Selenium divergence: {@code TakesScreenshot} on the driver captures the visible viewport
 * (not the full document), so the normalized point the model returns is mapped to viewport pixels
 * and resolved with {@code document.elementFromPoint} directly — no document-height scaling.
 */
public final class Vision {
  private Vision() {}

  static final String HEAL_ATTRIBUTE = "data-tamash-heal-id";

  public record ViewportBox(double x, double y, double width, double height) {}

  public record VisionCapture(String imageBase64, ViewportBox box, double scrollX, double scrollY) {}

  public record ResolvedVisionPoint(String id, double viewportX, double viewportY) {}

  public static VisionCapture captureElementForVision(PageContext ctx, double timeoutMs) {
    try {
      byte[] png = ((TakesScreenshot) ctx.driver()).getScreenshotAs(OutputType.BYTES);
      Object dims = ctx.js().executeScript(
          "return {w: window.innerWidth, h: window.innerHeight, sx: window.scrollX, sy: window.scrollY};");
      double w = 0;
      double h = 0;
      double sx = 0;
      double sy = 0;
      if (dims instanceof Map<?, ?> m) {
        w = num(m.get("w"));
        h = num(m.get("h"));
        sx = num(m.get("sx"));
        sy = num(m.get("sy"));
      }
      if (w <= 0 || h <= 0) {
        return null;
      }
      return new VisionCapture(Base64.getEncoder().encodeToString(png), new ViewportBox(0, 0, w, h), sx, sy);
    } catch (Exception e) {
      return null;
    }
  }

  private static final String RESOLVE_JS = """
      var coords = arguments[0];
      var vx = coords[0], vy = coords[1];
      var el = document.elementFromPoint(vx, vy);
      if (!el || el === document.documentElement || el === document.body) { return null; }
      var generatedId = 'tamash-heal-' + Math.random().toString(36).slice(2);
      el.setAttribute('data-tamash-heal-id', generatedId);
      return { id: generatedId, viewportX: vx, viewportY: vy };
      """;

  public static ResolvedVisionPoint resolveElementAtVisionPoint(
      PageContext ctx, VisionPoint point, ViewportBox box, double scrollX, double scrollY, double timeoutMs) {
    double vx = (point.getX() / 1000.0) * box.width();
    double vy = (point.getY() / 1000.0) * box.height();
    try {
      Object result = ctx.js().executeScript(RESOLVE_JS, List.of(vx, vy));
      if (!(result instanceof Map<?, ?> m) || m.get("id") == null) {
        return null;
      }
      return new ResolvedVisionPoint(String.valueOf(m.get("id")), num(m.get("viewportX")), num(m.get("viewportY")));
    } catch (Exception e) {
      return null;
    }
  }

  public static org.openqa.selenium.By visionTagSelector(String id) {
    return org.openqa.selenium.By.cssSelector("[" + HEAL_ATTRIBUTE + "=\"" + id + "\"]");
  }

  public static void cleanupVisionTag(PageContext ctx, String id) {
    try {
      ctx.js().executeScript(
          "var e = document.querySelector('[data-tamash-heal-id=\"' + arguments[0] + '\"]'); if (e) e.removeAttribute('data-tamash-heal-id');",
          id);
    } catch (Exception ignored) {
      // best-effort
    }
  }

  private static double num(Object o) {
    return o instanceof Number n ? n.doubleValue() : 0;
  }
}
