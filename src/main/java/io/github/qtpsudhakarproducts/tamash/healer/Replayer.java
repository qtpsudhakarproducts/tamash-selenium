package io.github.qtpsudhakarproducts.tamash.healer;

import org.openqa.selenium.WebElement;

/** Re-runs the original failed action against a (healed) element. In the Java port this is the
 *  same reflected {@code Method} re-invoked on the freshly-found element — inherently forwarding
 *  every original argument, which is what src/healer/replay-action.ts hand-codes per method in TS. */
@FunctionalInterface
public interface Replayer {
  Object replay(WebElement element) throws Throwable;
}
