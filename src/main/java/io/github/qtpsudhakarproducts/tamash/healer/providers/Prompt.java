package io.github.qtpsudhakarproducts.tamash.healer.providers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of src/healer/providers/prompt.ts — the shared system prompts, user-prompt builders, and
 * lenient JSON response parsers for all providers (text healing, vision, action recovery).
 * Adapted for Selenium: the snapshot is a JS-serialized DOM accessibility tree, and the fallback
 * strategies are native Selenium locators (css / xpath / id / name / link text).
 */
public final class Prompt {
  private Prompt() {}

  private static final int MAX_SNAPSHOT_CHARS = 16000;

  public static final String SYSTEM_PROMPT = """
      You are a Selenium self-healing assistant.
      You will be given a DOM accessibility snapshot of a web page, an action that just failed, and a human description of the element the test intended to interact with. Every element in the snapshot has its own [ref=eN] id, and where available its on-screen position as [box=x,y,width,height] in CSS pixels. The snapshot preserves the real parent/child/sibling structure of the page — read it as a tree, not a flat list.
      Pick the single best-matching element and respond with strict JSON only (no markdown, no prose):
      {"strategy":"ref","ref":"<the [ref=...] id of the target element itself>","nearbyRef":"<optional: the [ref=...] id of a nearby label/heading/element that told you what this is, if it has one>","nearbyText":"<optional: the literal nearby text that told you what this is, if it's plain text with no ref of its own>","nearbyRole":"<optional: that nearby text/element's own role in the snapshot, e.g. \\"text\\", \\"legend\\", \\"heading\\">"}
      Prefer "ref" whenever you can identify the target element anywhere in the snapshot tree, even if it has no accessible name of its own (a plain, unlabelled "textbox" or "generic" node is a perfectly good ref to pick — a separate step on our side reads the tree structure around it to build a resilient permanent selector, the same way a sighted user would read a nearby label before writing one by hand). When the target has no accessible name of its own, also report whatever nearby label/heading/text actually told you what it is — include nearbyRef if that nearby thing has its own [ref=...], or nearbyText (plus its nearbyRole) if it's plain text with none. Include whichever apply; omit any you don't have.
      Only if NOTHING in the snapshot plausibly matches the description, fall back to one of:
      {"strategy":"id","id":"<the element's id attribute>"}
      {"strategy":"name","name":"<the element's name attribute>"}
      {"strategy":"css","css":"<a CSS selector>"}
      {"strategy":"xpath","xpath":"<an XPath expression>"}
      {"strategy":"text","text":"<exact visible text of a link or button>"}
      {"strategy":"near","anchorText":"<nearby visible text>","role":"<html tag or aria role of the TARGET element itself, e.g. input, button, select>"}
      {"strategy":"none"}
      Never invent a ref, element, attribute, or id that isn't literally in the snapshot.""";

  public static String buildUserPrompt(SuggestSelectorInput input) {
    String snapshot = input.getAriaSnapshot();
    if (snapshot != null && snapshot.length() > MAX_SNAPSHOT_CHARS) {
      snapshot = snapshot.substring(0, MAX_SNAPSHOT_CHARS) + "\n... (truncated)";
    }
    String description = input.getDescription();
    String descriptionText = (description == null || description.isEmpty()) ? "(none provided)" : description;
    String action = input.getAction() != null ? input.getAction() : "(unknown — element not found)";

    StringBuilder sb = new StringBuilder();
    sb.append("Failed action: ").append(action).append("\n\n");
    sb.append("Element description: ").append(descriptionText).append('\n');
    appendHint(sb, "Locator variable/field name", input.getRawName(), description);
    appendHint(sb, "Broken selector (no longer matches)", input.getBrokenSelector(), description);
    appendHint(sb, "Defined in class", input.getContextClass(), null);
    sb.append("\nDOM snapshot:\n\n").append(snapshot);
    return sb.toString();
  }

  /** Adds a hint line only when it's present and adds signal the description doesn't already carry. */
  private static void appendHint(StringBuilder sb, String label, String value, String description) {
    if (value == null || value.isBlank()) {
      return;
    }
    String v = value.trim();
    if (description != null && description.equalsIgnoreCase(v)) {
      return;
    }
    sb.append(label).append(": ").append(v).append('\n');
  }

  public static final String VISION_SYSTEM_PROMPT = """
      You are a Selenium self-healing assistant.
      You will be given a screenshot of a web page, an action that just failed, and a human description of the element the test intended to interact with.
      Find the single best-matching element that is LITERALLY visible in the screenshot and respond with strict JSON only (no markdown, no prose):
      {"found":true,"x":<0-1000>,"y":<0-1000>}
      Where x and y are the CENTER of the matching element, expressed as its position within the image scaled to a 0-1000 range (0 = left/top edge, 1000 = right/bottom edge) — NOT raw pixel coordinates.
      If nothing in the screenshot plausibly matches the description, respond with {"found":false}. Never invent an element that isn't visible in the image.""";

  public static String buildVisionUserPrompt(SuggestElementFromImageInput input) {
    String description = input.getDescription();
    String action = input.getAction() != null ? input.getAction() : "(unknown — element not found)";
    return "Failed action: " + action + "\n\n"
        + "Element description: " + (description == null || description.isEmpty() ? "(none provided)" : description) + "\n\n"
        + "A screenshot of the page is attached.";
  }

  public static final String ACTION_RECOVERY_SYSTEM_PROMPT = """
      You are a Selenium self-healing assistant.
      An element was already correctly located, but the action on it failed for the reason described below — this is NOT a selector problem, so do not suggest one. Given the error, choose the single tactic most likely to help:
      {"tactic":"scroll"} — the element may be outside the current viewport; scroll it into view, then retry the same action.
      {"tactic":"force"} — the error looks transient or overly strict rather than something genuinely blocking the interaction; retry the same action via a direct JavaScript / Actions call that bypasses Selenium's standard interactability checks. Note: this still targets the element itself, so it will NOT help if another element is genuinely covering this one — use "dispatch" for that instead.
      {"tactic":"wait"} — the error suggests something transient (an animation or transition still settling, content still loading); wait briefly, then retry the same action.
      {"tactic":"dispatch"} — the element is genuinely covered/intercepted by another element on top of it, or force did not help; dispatch the underlying DOM event directly on the element via JavaScript instead of a real interaction. Last resort, since it skips real hit-testing entirely.
      {"tactic":"none"} — none of the above would plausibly help.
      Respond with strict JSON only (no markdown, no prose), exactly one of the five objects above.""";

  public static String buildActionRecoveryUserPrompt(SuggestActionTacticInput input) {
    return "Failed action: " + input.getAction() + "\n\n" + "Error: " + input.getErrorMessage();
  }

  // ---- parsing ------------------------------------------------------------

  private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

  private static JSONObject lenientParse(String content) {
    if (content == null) return null;
    JSONObject parsed = tryParseJson(content);
    if (parsed != null) return parsed;
    Matcher m = JSON_OBJECT_PATTERN.matcher(content);
    return m.find() ? tryParseJson(m.group()) : null;
  }

  private static JSONObject tryParseJson(String content) {
    try {
      return new JSONObject(content);
    } catch (JSONException e) {
      return null;
    }
  }

  private static String str(JSONObject o, String key) {
    return o.has(key) && !o.isNull(key) ? o.optString(key, null) : null;
  }

  public static AiSuggestion parseSuggestion(String content) {
    JSONObject parsed = lenientParse(content);
    if (parsed == null || !parsed.has("strategy")) return null;
    String strategy = parsed.optString("strategy", null);
    if (strategy == null) return null;

    switch (strategy) {
      case "none":
        return AiSuggestion.none();
      case "ref": {
        String ref = str(parsed, "ref");
        return ref == null ? null
            : AiSuggestion.ref(ref, str(parsed, "nearbyRef"), str(parsed, "nearbyText"), str(parsed, "nearbyRole"));
      }
      case "id": {
        String id = str(parsed, "id");
        return id == null ? null : AiSuggestion.id(id);
      }
      case "name": {
        String name = str(parsed, "name");
        return name == null ? null : AiSuggestion.nameAttr(name);
      }
      case "css": {
        String css = str(parsed, "css");
        return css == null ? null : AiSuggestion.css(css);
      }
      case "xpath": {
        String xpath = str(parsed, "xpath");
        return xpath == null ? null : AiSuggestion.xpath(xpath);
      }
      case "text": {
        String text = str(parsed, "text");
        return text == null ? null : AiSuggestion.text(text);
      }
      case "near": {
        String anchorText = str(parsed, "anchorText");
        String role = str(parsed, "role");
        return (anchorText == null || role == null) ? null : AiSuggestion.near(anchorText, role, null);
      }
      case "adjacent": {
        String anchorText = str(parsed, "anchorText");
        String role = str(parsed, "role");
        return (anchorText == null || role == null) ? null
            : AiSuggestion.adjacent(anchorText, role, parsed.has("anchorClimbLevels") ? parsed.optInt("anchorClimbLevels") : null);
      }
      case "scoped": {
        String containerRole = str(parsed, "containerRole");
        String role = str(parsed, "role");
        return (containerRole == null || role == null) ? null
            : AiSuggestion.scoped(containerRole, str(parsed, "containerName"), role, str(parsed, "name"));
      }
      case "containing": {
        String role = str(parsed, "role");
        String anchorText = str(parsed, "anchorText");
        return (role == null || anchorText == null) ? null : AiSuggestion.containing(role, anchorText);
      }
      // A `role` the model reports despite the prompt — fold into an approximate css selector.
      case "role": {
        String role = str(parsed, "role");
        if (role == null) return null;
        String name = str(parsed, "name");
        return name != null
            ? AiSuggestion.xpath("//*[@role='" + role + "' or local-name()='" + role
                + "'][normalize-space(.)=" + xpathLiteral(name) + " or @aria-label=" + xpathLiteral(name) + "]")
            : AiSuggestion.css("[role='" + role + "']");
      }
      default:
        return null;
    }
  }

  /** Wraps a string as an XPath string literal, using concat() when it contains both quote kinds. */
  static String xpathLiteral(String value) {
    if (value == null) return "''";
    if (!value.contains("'")) return "'" + value + "'";
    if (!value.contains("\"")) return "\"" + value + "\"";
    StringBuilder sb = new StringBuilder("concat(");
    String[] parts = value.split("'", -1);
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) sb.append(", \"'\", ");
      sb.append("'").append(parts[i]).append("'");
    }
    return sb.append(")").toString();
  }

  public static VisionPoint parseVisionSuggestion(String content) {
    JSONObject parsed = lenientParse(content);
    if (parsed == null || !parsed.has("found")) return null;
    if (!parsed.optBoolean("found", false)) {
      return VisionPoint.notFound();
    }
    if (!parsed.has("x") || !parsed.has("y")) return null;
    double x = parsed.optDouble("x", -1);
    double y = parsed.optDouble("y", -1);
    if (x < 0 || x > 1000 || y < 0 || y > 1000) return null;
    return VisionPoint.at(x, y);
  }

  public static ActionTactic parseActionTacticSuggestion(String content) {
    JSONObject parsed = lenientParse(content);
    if (parsed == null || !parsed.has("tactic")) return null;
    return ActionTactic.fromWire(parsed.optString("tactic", null));
  }

  // ---- usage helpers ----------------------------------------------------

  /** Shared by any provider whose HTTP response mimics OpenAI's Chat Completions shape. */
  public static TokenUsage extractOpenAiCompatibleUsage(JSONObject payload) {
    JSONObject usage = payload.optJSONObject("usage");
    if (usage == null) return null;
    return new TokenUsage(
        optIntOrNull(usage, "prompt_tokens"),
        optIntOrNull(usage, "completion_tokens"),
        optIntOrNull(usage, "total_tokens"));
  }

  public static Integer optIntOrNull(JSONObject obj, String key) {
    return (obj != null && obj.has(key) && !obj.isNull(key)) ? obj.optInt(key) : null;
  }
}
