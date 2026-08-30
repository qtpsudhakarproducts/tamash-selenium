package io.github.qtpsudhakarproducts.tamash.healer.providers;

/**
 * A fixed, auditable menu — the AI only ever picks among these, it never invents a new way to
 * interact with the page. Used only after a locator has already been successfully healed (via
 * text or vision) but replaying the original action on it still failed for a non-selector reason
 * (covered, needs scrolling, mid-animation, ...).
 */
public enum ActionTactic {
  SCROLL, FORCE, WAIT, DISPATCH, NONE;

  /** Parses the lowercase wire value ({@code "scroll"} etc.); returns null for anything else. */
  public static ActionTactic fromWire(String value) {
    if (value == null) return null;
    return switch (value.trim().toLowerCase()) {
      case "scroll" -> SCROLL;
      case "force" -> FORCE;
      case "wait" -> WAIT;
      case "dispatch" -> DISPATCH;
      case "none" -> NONE;
      default -> null;
    };
  }
}
