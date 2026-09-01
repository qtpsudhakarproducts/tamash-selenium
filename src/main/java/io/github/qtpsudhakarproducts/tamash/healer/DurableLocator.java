package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.AiSuggestion;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of src/healer/durable-locator.ts (cross-checked against the Python port's
 * durable_locator.py for non-JS decisions). The tree-reading half — {@link #parseAriaAiTree},
 * {@link #findAdjacentBranchPath}, {@link #findSiblingAnchorTexts}, {@link #extractScopedSnapshot},
 * {@link #findRuleBasedMatch} — is DOM-agnostic and carried
 * over verbatim; it parses the YAML tree emitted by {@link DomSnapshot}.
 *
 * <p>The Playwright-specific half is replaced with Selenium equivalents: {@link #sameElement}
 * (JS identity check), {@link #deriveSuggestionFromElement} (a stable {@code By} from a resolved
 * element, replacing {@code Locator.normalize()}), {@link #toBy} (an {@link AiSuggestion} → a real
 * {@link By}), and {@link #generateReplacementCall} (an {@link AiSuggestion} → the {@code By.…}
 * Java source {@code apply-heals} writes and the console/report shows).
 */
public final class DurableLocator {
  private DurableLocator() {}

  static final String REF_ATTRIBUTE = "data-tamash-ref";

  // ---- DOM identity check --------------------------------------------------

  /** DOM identity, not markup or position: two elements can share identical outerHTML or an
   *  identical bounding box while being genuinely different nodes. */
  public static boolean sameElement(WebDriver driver, WebElement a, WebElement b) {
    if (a == null || b == null) {
      return false;
    }
    try {
      Object same = ((JavascriptExecutor) driver).executeScript("return arguments[0] === arguments[1];", a, b);
      return Boolean.TRUE.equals(same);
    } catch (Exception e) {
      return false;
    }
  }

  // ---- ARIA AI tree parsing (verbatim from the Playwright port) -----------

  public record Box(double x, double y, double width, double height) {}

  public record AriaAiNode(int depth, int lineIndex, String role, String name, String ref, Box box, String text) {
    static AriaAiNode text(int depth, int lineIndex, String text) {
      return new AriaAiNode(depth, lineIndex, null, null, null, null, text);
    }
  }

  private static final Pattern LINE_RE = Pattern.compile("^(\\s*)-\\s(.*)$");
  private static final Pattern TEXT_RE = Pattern.compile("^text:\\s?(.*)$");
  private static final Pattern REF_RE = Pattern.compile("\\[ref=([^\\]]+)\\]");
  private static final Pattern BOX_RE =
      Pattern.compile("\\[box=(-?[\\d.]+),(-?[\\d.]+),(-?[\\d.]+),(-?[\\d.]+)\\]");
  private static final Pattern NAME_RE = Pattern.compile("^(.+?)\\s*\"([^\"]*)\"$");
  private static final Pattern TRAILING_RE = Pattern.compile("^:\\s*(?:\"([^\"]*)\"|(.+))$");

  /** Parses a {@link DomSnapshot} YAML tree into a flat, depth-annotated node list. */
  public static List<AriaAiNode> parseAriaAiTree(String snapshot) {
    List<AriaAiNode> nodes = new ArrayList<>();
    if (snapshot == null) {
      return nodes;
    }
    String[] lines = snapshot.split("\n", -1);
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String rawLine = lines[lineIndex];
      Matcher lm = LINE_RE.matcher(rawLine);
      if (!lm.matches()) {
        continue;
      }
      int depth = lm.group(1).length() / 2;
      String content = lm.group(2);

      Matcher tm = TEXT_RE.matcher(content);
      if (tm.matches()) {
        nodes.add(AriaAiNode.text(depth, lineIndex, tm.group(1).trim()));
        continue;
      }

      if (content.startsWith("/")) {
        continue;
      }

      if (content.length() >= 2 && content.charAt(0) == '\'' && content.charAt(content.length() - 1) == '\'') {
        content = content.substring(1, content.length() - 1);
      }

      Matcher rm = REF_RE.matcher(content);
      if (!rm.find()) {
        continue;
      }
      String ref = rm.group(1);

      Box box = null;
      Matcher bm = BOX_RE.matcher(content);
      if (bm.find()) {
        box = new Box(Double.parseDouble(bm.group(1)), Double.parseDouble(bm.group(2)),
            Double.parseDouble(bm.group(3)), Double.parseDouble(bm.group(4)));
      }

      int firstBracket = content.indexOf('[');
      String head = (firstBracket == -1 ? content : content.substring(0, firstBracket)).trim();
      Matcher nm = NAME_RE.matcher(head);
      boolean hasQuotedName = nm.matches();
      String role;
      if (hasQuotedName) {
        role = nm.group(1).trim();
      } else {
        role = head.replaceAll(":$", "").trim();
      }
      if (role.isEmpty()) {
        role = null;
      }

      int lastBracketEnd = content.lastIndexOf(']');
      String trailing = lastBracketEnd == -1 ? "" : content.substring(lastBracketEnd + 1);
      Matcher trm = TRAILING_RE.matcher(trailing);
      String trailingText = null;
      if (trm.matches()) {
        trailingText = (trm.group(1) != null ? trm.group(1) : trm.group(2)).trim();
      }

      String name = hasQuotedName ? nm.group(2) : trailingText;
      nodes.add(new AriaAiNode(role != null ? depth : depth, lineIndex, role, name, ref, box, null));
    }
    return nodes;
  }

  // ---- role inference ---------------------------------------------------

  /** Deterministic — the action already implies the target's control kind. */
  public static String inferRoleFromAction(String action) {
    if (action == null) {
      return null;
    }
    return switch (action) {
      case "fill", "sendKeys", "type", "pressSequentially", "clear" -> "textbox";
      case "check", "uncheck", "setChecked" -> "checkbox";
      case "selectOption" -> "combobox";
      case "submit" -> "button";
      default -> null;
    };
  }

  // ---- generic role suffix stripping ----------------------------------

  private static final Pattern GENERIC_ROLE_SUFFIX = Pattern.compile(
      "\\s+(text ?box|input|field|drop ?down|select|combo ?box|button|link|check ?box|radio( ?button)?|label|icon|list ?box|menu)$",
      Pattern.CASE_INSENSITIVE);

  public static String stripGenericRoleSuffix(String description) {
    if (description == null) {
      return null;
    }
    return GENERIC_ROLE_SUFFIX.matcher(description).replaceAll("").trim();
  }

  // ---- sibling anchor search (verbatim) ------------------------------

  public record SiblingAnchorCandidate(String text, int levelsUp) {}

  private static int findIndexByRef(List<AriaAiNode> nodes, String ref) {
    for (int i = 0; i < nodes.size(); i++) {
      if (ref.equals(nodes.get(i).ref())) {
        return i;
      }
    }
    return -1;
  }

  private static Integer findAncestor(List<AriaAiNode> nodes, int fromIndex, int wantDepth) {
    for (int i = fromIndex - 1; i >= 0; i--) {
      if (nodes.get(i).depth() == wantDepth) {
        return i;
      }
      if (nodes.get(i).depth() < wantDepth) {
        return null;
      }
    }
    return null;
  }

  public static List<SiblingAnchorCandidate> findSiblingAnchorTexts(List<AriaAiNode> nodes, String targetRef, int maxLevels) {
    int targetIndex = findIndexByRef(nodes, targetRef);
    if (targetIndex == -1) {
      return List.of();
    }
    AriaAiNode target = nodes.get(targetIndex);

    List<SiblingAnchorCandidate> results = new ArrayList<>();
    int ancestorIndex = targetIndex;
    int ancestorDepth = target.depth();

    for (int levelsUp = 1; levelsUp <= maxLevels; levelsUp++) {
      Integer parentIndex = findAncestor(nodes, ancestorIndex, ancestorDepth - 1);
      if (parentIndex == null) {
        break;
      }
      int parentDepth = nodes.get(parentIndex).depth();
      int siblingDepth = ancestorDepth;

      List<int[]> candidateLineIdx = new ArrayList<>();
      List<String> candidateText = new ArrayList<>();
      for (int i = parentIndex + 1; i < nodes.size(); i++) {
        AriaAiNode n = nodes.get(i);
        if (n.depth() <= parentDepth) {
          break;
        }
        if (n.depth() != siblingDepth) {
          continue;
        }
        if (targetRef.equals(n.ref())) {
          continue;
        }
        if (n.name() != null) {
          candidateText.add(n.name());
          candidateLineIdx.add(new int[]{candidateText.size() - 1, n.lineIndex()});
        }
        if (n.text() != null) {
          candidateText.add(n.text());
          candidateLineIdx.add(new int[]{candidateText.size() - 1, n.lineIndex()});
        }
      }

      final int targetLine = target.lineIndex();
      candidateLineIdx.sort((p, q) -> Integer.compare(Math.abs(p[1] - targetLine), Math.abs(q[1] - targetLine)));
      final int lvl = levelsUp;
      for (int[] c : candidateLineIdx) {
        results.add(new SiblingAnchorCandidate(candidateText.get(c[0]), lvl));
      }

      ancestorIndex = parentIndex;
      ancestorDepth = parentDepth;
    }

    Set<String> seen = new HashSet<>();
    List<SiblingAnchorCandidate> filtered = new ArrayList<>();
    for (SiblingAnchorCandidate c : results) {
      if (seen.add(c.text())) {
        filtered.add(c);
      }
    }
    return filtered;
  }

  public static List<SiblingAnchorCandidate> findSiblingAnchorTexts(List<AriaAiNode> nodes, String targetRef) {
    return findSiblingAnchorTexts(nodes, targetRef, 2);
  }

  // ---- adjacent branch detection (verbatim) -------------------------

  public record AdjacentBranchPath(int anchorClimbLevels) {}

  private static List<Integer> ancestorChain(List<AriaAiNode> nodes, int index) {
    List<Integer> chain = new ArrayList<>();
    chain.add(index);
    int depth = nodes.get(index).depth();
    for (int i = index - 1; i >= 0; i--) {
      if (nodes.get(i).depth() < depth) {
        chain.add(i);
        depth = nodes.get(i).depth();
      }
    }
    return chain;
  }

  public static AdjacentBranchPath findAdjacentBranchPath(List<AriaAiNode> nodes, String targetRef, String anchorRef) {
    int targetIndex = findIndexByRef(nodes, targetRef);
    int anchorIndex = findIndexByRef(nodes, anchorRef);
    if (targetIndex == -1 || anchorIndex == -1) {
      return null;
    }

    List<Integer> anchorChain = ancestorChain(nodes, anchorIndex);
    List<Integer> targetChain = ancestorChain(nodes, targetIndex);
    Set<Integer> targetChainSet = new HashSet<>(targetChain);

    for (int i = 0; i < anchorChain.size(); i++) {
      if (!targetChainSet.contains(anchorChain.get(i))) {
        continue;
      }
      int targetChainIdx = targetChain.indexOf(anchorChain.get(i));
      if (i == 0 && targetChainIdx == 0) {
        return null;
      }
      if (i == 0 || targetChainIdx == 0) {
        return null;
      }

      int anchorBranchIndex = anchorChain.get(i - 1);
      int targetBranchIndex = targetChain.get(targetChainIdx - 1);
      int branchDepth = nodes.get(anchorBranchIndex).depth();

      if (nodes.get(targetBranchIndex).depth() != branchDepth || targetBranchIndex <= anchorBranchIndex) {
        return null;
      }
      for (int k = anchorBranchIndex + 1; k < targetBranchIndex; k++) {
        if (nodes.get(k).depth() <= branchDepth) {
          return null;
        }
      }
      return new AdjacentBranchPath(nodes.get(anchorIndex).depth() - branchDepth);
    }
    return null;
  }

  // ---- scoped snapshot extraction (verbatim) -----------------------

  public static String extractScopedSnapshot(String fullSnapshotText, String phrase) {
    List<AriaAiNode> nodes = parseAriaAiTree(fullSnapshotText);
    String needle = phrase.toLowerCase();
    List<Integer> matchIdxs = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i++) {
      AriaAiNode n = nodes.get(i);
      boolean hit = (n.text() != null && n.text().toLowerCase().contains(needle))
          || (n.name() != null && n.name().toLowerCase().contains(needle));
      if (hit) {
        matchIdxs.add(i);
      }
    }
    if (matchIdxs.size() != 1) {
      return null;
    }
    int matchIndex = matchIdxs.get(0);

    Set<Integer> included = new HashSet<>();
    includeSubtree(nodes, matchIndex, included);

    int idx = matchIndex;
    int depth = nodes.get(matchIndex).depth();
    while (depth > 0) {
      Integer parentIndex = null;
      for (int k = idx - 1; k >= 0; k--) {
        if (nodes.get(k).depth() == depth - 1) {
          parentIndex = k;
          break;
        }
        if (nodes.get(k).depth() < depth - 1) {
          break;
        }
      }
      if (parentIndex == null) {
        break;
      }
      included.add(parentIndex);
      for (int k = parentIndex + 1; k < nodes.size(); k++) {
        if (nodes.get(k).depth() <= depth - 1) {
          break;
        }
        if (nodes.get(k).depth() == depth) {
          includeSubtree(nodes, k, included);
        }
      }
      idx = parentIndex;
      depth -= 1;
    }

    Set<Integer> includedLines = new HashSet<>();
    for (int i : included) {
      includedLines.add(nodes.get(i).lineIndex());
    }
    String[] lines = fullSnapshotText.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (includedLines.contains(i)) {
        if (sb.length() > 0) {
          sb.append('\n');
        }
        sb.append(lines[i]);
      }
    }
    return sb.toString();
  }

  private static void includeSubtree(List<AriaAiNode> nodes, int i, Set<Integer> included) {
    int depth = nodes.get(i).depth();
    included.add(i);
    for (int k = i + 1; k < nodes.size(); k++) {
      if (nodes.get(k).depth() <= depth) {
        break;
      }
      included.add(k);
    }
  }

  // ---- rule-based matching (for the `tamash` provider) — verbatim --

  private static final Set<String> INTERACTIVE_ROLES = Set.of(
      "button", "link", "checkbox", "radio", "combobox", "textbox", "switch", "tab", "menuitem", "option");

  private static final Map<String, String> ROLE_SYNONYMS = Map.of(
      "dropdown", "combobox", "select", "combobox", "radio button", "radio");

  private static String normalizeRoleForMatch(String role) {
    if (role == null) {
      return null;
    }
    String key = role.toLowerCase().trim();
    return ROLE_SYNONYMS.getOrDefault(key, key);
  }

  private static boolean roleIsPlausibleTarget(String actualRole, String expectedRole) {
    String actual = normalizeRoleForMatch(actualRole);
    if (actual == null) {
      return false;
    }
    if (expectedRole == null) {
      return INTERACTIVE_ROLES.contains(actual);
    }
    return actual.equals(normalizeRoleForMatch(expectedRole));
  }

  // Words that carry no matching signal in a field description ("Username field", "the Save button").
  private static final Set<String> MATCH_STOPWORDS = Set.of(
      "the", "a", "an", "field", "input", "box", "textbox", "button", "link", "icon",
      "dropdown", "select", "checkbox", "radio", "label", "element", "control");

  public static AiSuggestion findRuleBasedMatch(List<AriaAiNode> nodes, String phrase, String expectedRole) {
    String needle = phrase.toLowerCase();
    List<AriaAiNode> matches = new ArrayList<>();
    for (AriaAiNode n : nodes) {
      boolean hit = (n.text() != null && n.text().toLowerCase().contains(needle))
          || (n.name() != null && n.name().toLowerCase().contains(needle));
      if (hit) {
        matches.add(n);
      }
    }

    // No exact substring hit — fall back to token-set matching: a node whose accessible name /
    // nearby text contains every significant word of the phrase, in any order. Lets "First name
    // field" match a "First Name" label (which pure substring matching would miss).
    if (matches.isEmpty()) {
      List<String> tokens = new ArrayList<>();
      for (String w : needle.split("[^\\p{Alnum}]+")) {
        if (w.length() >= 2 && !MATCH_STOPWORDS.contains(w)) {
          tokens.add(w);
        }
      }
      if (!tokens.isEmpty()) {
        for (AriaAiNode n : nodes) {
          String hay = ((n.name() != null ? n.name() : "") + " " + (n.text() != null ? n.text() : "")).toLowerCase();
          if (!hay.isBlank() && tokens.stream().allMatch(hay::contains)) {
            matches.add(n);
          }
        }
      }
    }

    List<AriaAiNode> realCandidates = new ArrayList<>();
    for (AriaAiNode n : matches) {
      if (n.ref() != null && roleIsPlausibleTarget(n.role(), expectedRole)) {
        realCandidates.add(n);
      }
    }
    if (realCandidates.size() == 1) {
      return AiSuggestion.ref(realCandidates.get(0).ref(), null, null, null);
    }
    if (realCandidates.size() > 1) {
      return AiSuggestion.none();
    }

    if (matches.size() != 1) {
      return AiSuggestion.none();
    }
    AriaAiNode anchor = matches.get(0);
    int anchorIndex = nodes.indexOf(anchor);

    int idx = anchorIndex;
    int depth = anchor.depth();
    for (int levelsUp = 1; levelsUp <= 2 && depth > 0; levelsUp++) {
      Integer parentIndex = findAncestor(nodes, idx, depth - 1);
      if (parentIndex == null) {
        break;
      }
      int parentDepth = nodes.get(parentIndex).depth();
      List<AriaAiNode> candidates = new ArrayList<>();
      for (int i = parentIndex + 1; i < nodes.size(); i++) {
        AriaAiNode n = nodes.get(i);
        if (n.depth() <= parentDepth) {
          break;
        }
        if (i == anchorIndex) {
          continue;
        }
        if (n.ref() != null && roleIsPlausibleTarget(n.role(), expectedRole)) {
          candidates.add(n);
        }
      }
      if (candidates.size() == 1) {
        AriaAiNode target = candidates.get(0);
        return anchor.ref() != null
            ? AiSuggestion.ref(target.ref(), anchor.ref(), null, null)
            : AiSuggestion.ref(target.ref(), null,
                anchor.text() != null ? anchor.text() : anchor.name(),
                anchor.role() != null ? anchor.role() : "text");
      }
      if (candidates.size() > 1) {
        return AiSuggestion.none();
      }
      idx = parentIndex;
      depth = parentDepth;
    }
    return AiSuggestion.none();
  }


  // ---- positional-selector detection ----------------------------

  private static final Pattern POSITIONAL = Pattern.compile("\\[\\d+\\]\\s*$|:nth-child\\(|:nth-of-type\\(|following-sibling::\\*\\[1\\]");

  public static boolean isPositionalSelectorText(String selectorText) {
    return selectorText != null && POSITIONAL.matcher(selectorText).find();
  }

  // ---- deriving a stable By from a resolved element -------------

  // ids that carry no stable meaning — framework-generated / hashed / index-suffixed.
  private static final Pattern AUTO_ID = Pattern.compile(
      "^(:r[0-9a-z]+:|mui-\\d+|radix-|headlessui-|react-|ember\\d+|ext-gen\\d+|yui_|[0-9a-f]{8,}$).*|.*[-_]\\d{3,}$",
      Pattern.CASE_INSENSITIVE);

  static boolean looksAutoGenerated(String id) {
    if (id == null || id.isBlank()) {
      return true;
    }
    if (id.length() > 40) {
      return true;
    }
    return AUTO_ID.matcher(id).matches();
  }

  private static final String[] TESTID_ATTRS = {"data-testid", "data-test", "data-test-id", "data-cy", "data-qa"};

  /** Replaces {@code Locator.normalize()} — inspects a resolved element for a stable identity.
   *  Returns null when nothing durable stands on its own (the caller then widens to structural
   *  near/adjacent anchoring against the snapshot tree). */
  public static AiSuggestion deriveSuggestionFromElement(WebDriver driver, WebElement el) {
    if (el == null) {
      return null;
    }
    try {
      String id = attr(el, "id");
      if (id != null && !id.isBlank() && !looksAutoGenerated(id) && countCss(driver, "#" + cssEscape(id)) == 1) {
        return AiSuggestion.id(id);
      }
      String name = attr(el, "name");
      if (name != null && !name.isBlank() && countCss(driver, "[name=" + cssQuote(name) + "]") == 1) {
        return AiSuggestion.nameAttr(name);
      }
      for (String a : TESTID_ATTRS) {
        String v = attr(el, a);
        if (v != null && !v.isBlank()) {
          String css = "[" + a + "=" + cssQuote(v) + "]";
          if (countCss(driver, css) == 1) {
            return AiSuggestion.css(css);
          }
        }
      }
      String ariaLabel = attr(el, "aria-label");
      if (ariaLabel != null && !ariaLabel.isBlank()) {
        String css = "[aria-label=" + cssQuote(ariaLabel) + "]";
        if (countCss(driver, css) == 1) {
          return AiSuggestion.css(css);
        }
      }
      String placeholder = attr(el, "placeholder");
      if (placeholder != null && !placeholder.isBlank()) {
        String css = "[placeholder=" + cssQuote(placeholder) + "]";
        if (countCss(driver, css) == 1) {
          return AiSuggestion.css(css);
        }
      }
      String tag = safeTag(el);
      if ("a".equals(tag)) {
        String linkText = el.getText() != null ? el.getText().trim() : "";
        if (!linkText.isBlank() && linkText.length() <= 60
            && countXpath(driver, "//a[normalize-space(.)=" + xpathLiteral(linkText) + "]") == 1) {
          return AiSuggestion.text(linkText);
        }
      }
      String cls = attr(el, "class");
      if (cls != null && !cls.isBlank()) {
        String combo = tag + "." + String.join(".",
            java.util.Arrays.stream(cls.trim().split("\\s+"))
                .filter(c -> c.matches("[A-Za-z_][\\w-]*") && !looksAutoGenerated(c))
                .limit(3).toList());
        if (!combo.endsWith(".") && countCss(driver, combo) == 1) {
          return AiSuggestion.css(combo);
        }
      }

      // Attribute stacking: no single attribute is unique on its own, but two together might be
      // (input[type='password'][name='pwd']) — still far more stable than a positional XPath.
      String stacked = stackedAttributeCss(el, tag);
      if (stacked != null && countCss(driver, stacked) == 1) {
        return AiSuggestion.css(stacked);
      }
    } catch (Exception ignored) {
      // any driver hiccup — fall through to structural anchoring
    }
    return null;
  }

  private static final String[] STACKABLE_ATTRS = {"name", "type", "role", "placeholder", "aria-label", "title", "data-name"};

  /** A CSS selector stacking up to three stable, meaningful attributes of {@code el}. */
  private static String stackedAttributeCss(WebElement el, String tag) {
    StringBuilder sb = new StringBuilder(tag == null ? "*" : tag);
    int used = 0;
    for (String a : STACKABLE_ATTRS) {
      String v = attr(el, a);
      if (v != null && !v.isBlank() && v.length() <= 60 && !looksAutoGenerated(v)) {
        sb.append('[').append(a).append('=').append(cssQuote(v)).append(']');
        if (++used == 3) {
          break;
        }
      }
    }
    return used >= 2 ? sb.toString() : null;
  }

  private static String attr(WebElement el, String name) {
    try {
      return el.getDomAttribute(name);
    } catch (Exception e) {
      try { return el.getAttribute(name); } catch (Exception e2) { return null; }
    }
  }

  private static String safeTag(WebElement el) {
    try { return el.getTagName() == null ? null : el.getTagName().toLowerCase(); } catch (Exception e) { return null; }
  }

  private static int countCss(WebDriver driver, String css) {
    try { return driver.findElements(By.cssSelector(css)).size(); } catch (Exception e) { return -1; }
  }

  private static int countXpath(WebDriver driver, String xpath) {
    try { return driver.findElements(By.xpath(xpath)).size(); } catch (Exception e) { return -1; }
  }

  // ---- AiSuggestion -> By --------------------------------------

  /** Builds a real {@link By} from a persistable (or {@code ref}) suggestion. Returns null for
   *  {@code none} / an unrenderable shape. */
  public static By toBy(AiSuggestion s) {
    if (s == null) {
      return null;
    }
    return switch (s.getStrategy()) {
      case "ref" -> By.cssSelector("[" + REF_ATTRIBUTE + "='" + s.getRef() + "']");
      case "id" -> By.id(s.getId());
      case "name" -> By.name(s.getNameAttr());
      case "css" -> By.cssSelector(s.getCss());
      case "xpath" -> By.xpath(s.getXpath());
      case "text" -> By.xpath("//*[self::a or self::button][normalize-space(.)=" + xpathLiteral(s.getText())
          + "] | //*[normalize-space(text())=" + xpathLiteral(s.getText()) + "]");
      case "near" -> By.xpath(nearXpath(s.getAnchorText(), s.getRole(),
          s.getParentLevels() == null ? 1 : s.getParentLevels()));
      case "adjacent" -> By.xpath(adjacentXpath(s.getAnchorText(), s.getRole(),
          s.getAnchorClimbLevels() == null ? 0 : s.getAnchorClimbLevels()));
      case "scoped" -> By.xpath(scopedXpath(s.getContainerRole(), s.getContainerName(), s.getRole(), s.getName()));
      case "containing" -> By.xpath("//" + roleNodeTest(s.getRole())
          + "[contains(normalize-space(.), " + xpathLiteral(s.getAnchorText()) + ")]");
      case "normalized" -> {
        String code = s.getCode();
        if (code == null) yield null;
        yield code.trim().startsWith("/") || code.trim().startsWith("(") ? By.xpath(code) : By.cssSelector(code);
      }
      default -> null;
    };
  }

  private static String nearXpath(String anchorText, String role, int parentLevels) {
    StringBuilder sb = new StringBuilder("(//*[normalize-space(text())=" + xpathLiteral(anchorText) + "])[1]");
    for (int i = 0; i < Math.max(1, parentLevels); i++) {
      sb.append("/parent::*");
    }
    sb.append("/descendant-or-self::").append(roleNodeTest(role));
    return sb.toString();
  }

  private static String adjacentXpath(String anchorText, String role, int climb) {
    StringBuilder sb = new StringBuilder("(//*[normalize-space(text())=" + xpathLiteral(anchorText) + "])[1]");
    for (int i = 0; i < climb; i++) {
      sb.append("/parent::*");
    }
    sb.append("/following-sibling::*[1]/descendant-or-self::").append(roleNodeTest(role));
    return sb.toString();
  }

  private static String scopedXpath(String containerRole, String containerName, String role, String name) {
    String container = "//" + roleNodeTest(containerRole);
    if (containerName != null && !containerName.isBlank()) {
      container += "[@aria-label=" + xpathLiteral(containerName)
          + " or .//*[normalize-space(text())=" + xpathLiteral(containerName) + "]]";
    }
    String target = "descendant::" + roleNodeTest(role);
    if (name != null && !name.isBlank()) {
      target += "[@aria-label=" + xpathLiteral(name) + " or normalize-space(.)=" + xpathLiteral(name) + "]";
    }
    return container + "/" + target;
  }

  /** Maps an aria-ish role (or a raw HTML tag name) to an XPath node test. */
  static String roleNodeTest(String role) {
    if (role == null) {
      return "*";
    }
    return switch (role.toLowerCase().replaceAll("\\s+", "")) {
      case "textbox", "input" -> "*[self::input[not(@type) or @type='text' or @type='email' or @type='password' "
          + "or @type='search' or @type='tel' or @type='url' or @type='number'] or self::textarea]";
      case "button" -> "*[self::button or self::input[@type='button' or @type='submit' or @type='reset'] or @role='button']";
      case "checkbox" -> "input[@type='checkbox']";
      case "radio", "radiobutton" -> "input[@type='radio']";
      case "combobox", "dropdown", "select", "listbox" -> "*[self::select or @role='combobox' or @role='listbox']";
      case "link" -> "a";
      case "heading" -> "*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or @role='heading']";
      default -> {
        String safe = role.toLowerCase().replaceAll("[^a-z0-9-]", "");
        yield safe.isEmpty() ? "*" : "*[local-name()='" + safe + "' or @role='" + safe + "']";
      }
    };
  }

  // ---- replacement-call code generation -----------------------

  private static String lit(String value) {
    return org.json.JSONObject.quote(value);
  }

  /**
   * Turns a persistable {@link AiSuggestion} into the exact Java {@code By.…} call it describes —
   * the code {@code apply-heals} writes into source, and the string shown in console lines/reports.
   * Returns null for {@code ref}/{@code none}/an unrenderable {@code normalized}.
   */
  public static String generateReplacementCall(AiSuggestion s) {
    if (s == null) {
      return null;
    }
    By by = toBy(s);
    return by == null ? null : renderBy(by, s);
  }

  private static String renderBy(By by, AiSuggestion s) {
    return switch (s.getStrategy()) {
      case "id" -> "By.id(" + lit(s.getId()) + ")";
      case "name" -> "By.name(" + lit(s.getNameAttr()) + ")";
      case "css" -> "By.cssSelector(" + lit(s.getCss()) + ")";
      case "xpath" -> "By.xpath(" + lit(s.getXpath()) + ")";
      case "text" -> "By.xpath(" + lit(byXpathOf(by)) + ")";
      case "near", "adjacent", "scoped", "containing" -> "By.xpath(" + lit(byXpathOf(by)) + ")";
      case "normalized" -> {
        String code = s.getCode();
        if (code == null) yield null;
        yield code.trim().startsWith("/") || code.trim().startsWith("(")
            ? "By.xpath(" + lit(code) + ")" : "By.cssSelector(" + lit(code) + ")";
      }
      default -> null;
    };
  }

  /** {@code By.ByXPath} stringifies as {@code "By.xpath: <expr>"}. */
  private static String byXpathOf(By by) {
    String s = by.toString();
    int i = s.indexOf(':');
    return i == -1 ? s : s.substring(i + 1).trim();
  }

  /**
   * The {@code @FindBy} / {@code @FindBys} / {@code @FindAll} replacement {@code apply-heals} splices
   * over a PageFactory field's annotation — a single {@code @FindBy(...)} in every case. Returns
   * null for {@code ref}/{@code none}/an unrenderable {@code normalized}.
   */
  public static String generateFindByAnnotation(AiSuggestion s) {
    if (s == null) {
      return null;
    }
    return switch (s.getStrategy()) {
      case "id" -> "@FindBy(id = " + lit(s.getId()) + ")";
      case "name" -> "@FindBy(name = " + lit(s.getNameAttr()) + ")";
      case "css" -> "@FindBy(css = " + lit(s.getCss()) + ")";
      case "xpath" -> "@FindBy(xpath = " + lit(s.getXpath()) + ")";
      case "normalized" -> {
        String code = s.getCode();
        if (code == null) yield null;
        yield code.trim().startsWith("/") || code.trim().startsWith("(")
            ? "@FindBy(xpath = " + lit(code) + ")" : "@FindBy(css = " + lit(code) + ")";
      }
      default -> {
        By by = toBy(s);
        yield by == null ? null : "@FindBy(xpath = " + lit(byXpathOf(by)) + ")";
      }
    };
  }

  // ---- shared string helpers -------------------------------

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

  private static String cssQuote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String cssEscape(String ident) {
    return ident.replaceAll("([^A-Za-z0-9_-])", "\\\\$1");
  }
}
