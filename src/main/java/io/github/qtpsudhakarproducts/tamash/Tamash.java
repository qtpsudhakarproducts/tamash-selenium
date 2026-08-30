package io.github.qtpsudhakarproducts.tamash;

/**
 * The explicit description hook — for when the automatic call-site decode isn't enough (keyword-
 * driven suites, opaque locator names like {@code txtSSN}, heavy wrapper indirection). Set a hint
 * where your framework already carries the element's logical name, right before the interaction.
 *
 * <p>Recommended (auto-clears on scope exit):
 * <pre>{@code
 * // inside your shared wrapper, e.g. SeleniumWrapper.click(By locator, String name)
 * try (var h = Tamash.hint(name)) {
 *   getElement(locator).click();
 * }
 * }</pre>
 *
 * <p>Bare form also works — the hint is overwritten by the next {@link #hint} and cleared by the
 * framework integrations per test:
 * <pre>{@code
 * Tamash.hint("Username field");
 * driver.findElement(brokenLocator).sendKeys("admin");
 * }</pre>
 *
 * <p>The hint takes precedence over the decoded variable / field name and is also passed to the
 * AI provider as the element's name. It has no effect on whether a heal is attempted (assert-absent
 * / {@code HEALER_ASSERTIONS=strict} still apply).
 */
public final class Tamash {
  private Tamash() {}

  /** A hint scope — close it (or use try-with-resources) to restore the previous hint. */
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }

  private static final ThreadLocal<String> HINT = new ThreadLocal<>();

  public static Scope hint(String description) {
    String prev = HINT.get();
    if (description == null || description.isBlank()) {
      HINT.remove();
    } else {
      HINT.set(description.trim());
    }
    return () -> {
      if (prev != null) {
        HINT.set(prev);
      } else {
        HINT.remove();
      }
    };
  }

  public static void clearHint() {
    HINT.remove();
  }

  /** The active hint for this thread, or null. Read by the healer. */
  public static String currentHint() {
    return HINT.get();
  }
}
