package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.AiSuggestion;
import org.openqa.selenium.By;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-run heal cache — the thing that makes healing *inside* a {@code WebDriverWait}
 * affordable. A wait polls {@code findElement} many times; without this, each poll would trigger a
 * fresh snapshot + provider call.
 *
 * <ul>
 *   <li><b>Positive</b>: once {@code (brokenSelector, page)} has healed this run, every later
 *       failure for that selector — including the wait's next poll and any other caller — reuses
 *       the healed {@link By} instantly. Kept for the whole run (a repeated broken locator across
 *       the suite heals once).</li>
 *   <li><b>Negative</b>: if a heal was just attempted and <em>declined</em> for the current DOM
 *       state, don't retry until the DOM changes. So a genuine "not there yet" lets the wait poll
 *       normally — tamash tries roughly once per DOM state, not once per poll.</li>
 * </ul>
 */
public final class HealCache {
  private HealCache() {}

  private static final long NEGATIVE_TTL_MS = 3000;

  public record Hit(By healedBy, String describedAs, AiSuggestion suggestion) {}

  private static final Map<String, Hit> POSITIVE = new ConcurrentHashMap<>();
  private static final Map<String, Long> NEGATIVE = new ConcurrentHashMap<>();
  private static final Map<String, Integer> FAIL_COUNT = new ConcurrentHashMap<>();

  /** How many times this locator has failed to resolve this run. A locator broken inside a
   *  {@code WebDriverWait} on a still-loading SPA fails a poll or two then resolves — skipping the
   *  heal until the 2nd failure avoids a wasted snapshot/provider call on every such transient. */
  public static int recordFailing(By broken) {
    if (broken == null) {
      return 99;
    }
    return FAIL_COUNT.merge(broken.toString(), 1, Integer::sum);
  }

  /** How many times this locator has failed so far this run, without incrementing. */
  public static int failCount(By broken) {
    if (broken == null) {
      return 0;
    }
    Integer n = FAIL_COUNT.get(broken.toString());
    return n == null ? 0 : n;
  }

  public static Hit positive(By broken, String pageKey) {
    return broken == null ? null : POSITIVE.get(broken + "@" + safe(pageKey));
  }

  public static void recordPositive(By broken, String pageKey, By healed, String describedAs, AiSuggestion suggestion) {
    if (broken != null && healed != null) {
      POSITIVE.put(broken + "@" + safe(pageKey), new Hit(healed, describedAs, suggestion));
      HEALED_LOCATORS.add(broken.toString());
    }
  }

  private static final Set<String> HEALED_LOCATORS = ConcurrentHashMap.newKeySet();

  /** True if this locator was healed at least once this run (any page). */
  public static boolean everHealed(By broken) {
    return broken != null && HEALED_LOCATORS.contains(broken.toString());
  }

  public static boolean recentlyDeclined(By broken, String domKey) {
    if (broken == null) {
      return false;
    }
    Long t = NEGATIVE.get(broken + "#" + safe(domKey));
    return t != null && System.currentTimeMillis() - t < NEGATIVE_TTL_MS;
  }

  public static void recordDeclined(By broken, String domKey) {
    if (broken != null) {
      NEGATIVE.put(broken + "#" + safe(domKey), System.currentTimeMillis());
    }
  }

  /** Clears everything — the framework integrations call this per test to bound cross-test bleed;
   *  in the plain plug-and-play case the per-run cache is left alone. */
  public static void clear() {
    POSITIVE.clear();
    NEGATIVE.clear();
    FAIL_COUNT.clear();
    HEALED_LOCATORS.clear();
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
