package io.github.qtpsudhakarproducts.tamash.bindings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the source-location + variable-name-decoding logic in the TS/Python bindings
 * (src/bindings/locator.binding.ts, bindings.py). Resolves the consumer's real call site of a
 * locator-factory call and, when no {@code .describe()} was used, derives a human-readable
 * description from the locator's own variable name.
 *
 * <p>Java-specific: JS/Python get a filename directly from a stack frame. Java's
 * {@link StackTraceElement} gives a class name + a bare source file name + a line, so the
 * repo-relative path is recovered by probing the standard Maven source roots for
 * {@code <package-path>/<FileName>}.
 */
public final class SourceLocations {
  private SourceLocations() {}

  // The tamash machinery itself — a frame in one of these is never the consumer's call site.
  // Deliberately NOT the whole `io.github.qtpsudhakarproducts.tamash` root: this package's own
  // e2e tests (and a consumer whose package coincidentally starts the same way) live directly
  // under the root, and that test/page-object frame IS the call site we want.
  private static final List<String> INTERNAL_PREFIXES = List.of(
      "io.github.qtpsudhakarproducts.tamash.bindings.",
      "io.github.qtpsudhakarproducts.tamash.healer.",
      "io.github.qtpsudhakarproducts.tamash.junit.",
      "io.github.qtpsudhakarproducts.tamash.testng.",
      "io.github.qtpsudhakarproducts.tamash.cucumber.",
      "io.github.qtpsudhakarproducts.tamash.pagefactory.",
      "io.github.qtpsudhakarproducts.tamash.report.",
      "io.github.qtpsudhakarproducts.tamash.cli.");

  // Infrastructure frames between the call site and here.
  private static final List<String> INFRA_PREFIXES = List.of(
      "java.", "javax.", "jdk.", "sun.", "com.sun.proxy.",
      "org.openqa.selenium.",
      "org.junit.", "org.opentest4j.", "org.apiguardian.", "junit.", "org.testng.", "io.cucumber.",
      "org.apache.maven.", "org.apache.tools.", "org.gradle.", "worker.org.gradle.");

  private static final List<String> SOURCE_ROOTS = List.of(
      "src/test/java", "src/main/java", "src/it/java", "test", "src");

  /** The consumer's call site: a repo-relative {@code "path:line"} plus the simple name of the
   *  class it's in (a Page Object / test class — useful extra context for the AI finder). */
  public record Caller(String location, String simpleClassName) {}

  /** First stack frame that is neither tamash machinery nor test infrastructure. */
  public static Caller resolveCaller(Throwable callSite) {
    if (callSite == null) {
      return null;
    }
    if (System.getenv("TAMASH_DEBUG_STACK") != null || System.getProperty("TAMASH_DEBUG_STACK") != null) {
      System.err.println("--- callSite stack ---");
      for (StackTraceElement f : callSite.getStackTrace()) {
        System.err.println("  " + f.getClassName() + " (" + f.getFileName() + ":" + f.getLineNumber() + ")");
      }
    }
    for (StackTraceElement f : callSite.getStackTrace()) {
      String cls = f.getClassName();
      if (startsWithAny(cls, INTERNAL_PREFIXES) || startsWithAny(cls, INFRA_PREFIXES) || cls.startsWith("$")) {
        continue;
      }
      if (f.getFileName() == null || f.getLineNumber() <= 0) {
        continue;
      }
      String rel = toRepoRelative(cls, f.getFileName());
      String loc = (rel != null ? rel : f.getFileName()) + ":" + f.getLineNumber();
      return new Caller(loc, simpleClassName(cls));
    }
    return null;
  }

  /** First stack frame that is neither tamash machinery nor test infrastructure, as a
   *  repo-relative {@code "path:line"} — or null. */
  public static String resolveCallerLocation(Throwable callSite) {
    Caller c = resolveCaller(callSite);
    return c == null ? null : c.location();
  }

  /** The first {@code max} consumer frames (skipping Selenium / tamash / test infra). The extra
   *  frames let name resolution follow a locator passed through a `WebUtil.click(driver, loginBtn)`
   *  wrapper — the real name lives at the caller of the util, not inside it. */
  public static List<Caller> resolveConsumerChain(Throwable callSite, int max) {
    List<Caller> out = new java.util.ArrayList<>();
    if (callSite == null) {
      return out;
    }
    for (StackTraceElement f : callSite.getStackTrace()) {
      String cls = f.getClassName();
      if (startsWithAny(cls, INTERNAL_PREFIXES) || startsWithAny(cls, INFRA_PREFIXES) || cls.startsWith("$")) {
        continue;
      }
      if (f.getFileName() == null || f.getLineNumber() <= 0) {
        continue;
      }
      String rel = toRepoRelative(cls, f.getFileName());
      out.add(new Caller((rel != null ? rel : f.getFileName()) + ":" + f.getLineNumber(), simpleClassName(cls)));
      if (out.size() >= max) {
        break;
      }
    }
    return out;
  }

  static String simpleClassName(String fqcn) {
    if (fqcn == null) {
      return null;
    }
    int lastDot = fqcn.lastIndexOf('.');
    String s = lastDot == -1 ? fqcn : fqcn.substring(lastDot + 1);
    int lastDollar = s.lastIndexOf('$');
    if (lastDollar == -1) {
      return s;
    }
    String inner = s.substring(lastDollar + 1);
    return inner.isEmpty() || inner.matches("\\d+") ? s.substring(0, s.indexOf('$')) : inner;
  }

  private static final Pattern FIELD_DECL =
      Pattern.compile("(\\bWebElement\\b|\\bList\\s*<)");

  /**
   * Locates the {@code @FindBy} / {@code @FindBys} / {@code @FindAll} annotation that decorates a
   * PageFactory field, as a repo-relative {@code "path:line"} pointing at the annotation's first
   * line — so {@code apply-heals} can rewrite the annotation. Returns null if the source file or
   * the field can't be found.
   */
  public static String locateFindByField(Class<?> declaringClass, String fieldName) {
    if (declaringClass == null || fieldName == null) {
      return null;
    }
    Class<?> top = declaringClass;
    while (top.getEnclosingClass() != null) {
      top = top.getEnclosingClass();
    }
    String rel = toRepoRelative(declaringClass.getName(), top.getSimpleName() + ".java");
    if (rel == null) {
      return null;
    }
    Path path = Path.of("").toAbsolutePath().resolve(rel);
    List<String> lines;
    try {
      lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
    Pattern nameRef = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b\\s*(=|;)");
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!nameRef.matcher(line).find()) {
        continue;
      }
      boolean looksLikeField = FIELD_DECL.matcher(line).find()
          || (i > 0 && FIELD_DECL.matcher(lines.get(i - 1)).find());
      if (!looksLikeField) {
        continue;
      }
      int annLine = i;
      for (int j = i; j >= 0 && j >= i - 8; j--) {
        String t = lines.get(j).trim();
        if (t.startsWith("@FindBy") || t.startsWith("@FindBys") || t.startsWith("@FindAll")) {
          annLine = j;
          break;
        }
        if (j < i && !t.isEmpty() && !t.startsWith("@") && !t.startsWith(")") && !t.startsWith("(")
            && !t.endsWith(",") && !t.endsWith("{") && !t.startsWith("//") && !t.startsWith("*")) {
          break;
        }
      }
      return rel + ":" + (annLine + 1);
    }
    return null;
  }

  /** True if this failure surfaced from inside a {@code WebDriverWait}/{@code FluentWait} poll
   *  (an {@code org.openqa.selenium.support.ui.} frame on the stack). Such a locator fails once per
   *  poll; the healer treats the first few failures as "not here yet on a still-loading page". */
  public static boolean calledFromWait(Throwable site) {
    if (site == null) {
      return false;
    }
    for (StackTraceElement f : site.getStackTrace()) {
      if (f.getClassName().startsWith("org.openqa.selenium.support.ui.")) {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWithAny(String s, List<String> prefixes) {
    for (String p : prefixes) {
      if (s.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  private static String toRepoRelative(String className, String fileName) {
    int lastDot = className.lastIndexOf('.');
    String packagePath = lastDot == -1 ? "" : className.substring(0, lastDot).replace('.', '/');
    Path cwd = Path.of("").toAbsolutePath();
    for (String root : SOURCE_ROOTS) {
      Path candidate = cwd.resolve(root).resolve(packagePath).resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return cwd.relativize(candidate).toString().replace('\\', '/');
      }
    }
    return null;
  }

  // ---- variable-name inference --------------------------------------

  private static final Pattern VAR_NAME_RE = Pattern.compile("(\\w+)\\s*(?::\\s*[\\w.<>\\[\\]]+\\s*)?$");

  /** Reads the source line at {@code sourceLocation} and returns the identifier immediately before
   *  the line's first real {@code =} (keyword-agnostic: {@code this.x =}, {@code Locator x =},
   *  {@code var x =}, a bare reassignment). */
  public static String extractVariableName(String sourceLocation) {
    if (sourceLocation == null) {
      return null;
    }
    int sep = sourceLocation.lastIndexOf(':');
    if (sep == -1) {
      return null;
    }
    String filePath = sourceLocation.substring(0, sep);
    int lineNumber;
    try {
      lineNumber = Integer.parseInt(sourceLocation.substring(sep + 1));
    } catch (NumberFormatException e) {
      return null;
    }
    try {
      List<String> lines = Files.readAllLines(Path.of("").toAbsolutePath().resolve(filePath), StandardCharsets.UTF_8);
      if (lineNumber < 1 || lineNumber > lines.size()) {
        return null;
      }
      String line = lines.get(lineNumber - 1);
      int eq = findAssignmentEquals(line);
      if (eq == -1) {
        return null;
      }
      Matcher m = VAR_NAME_RE.matcher(line.substring(0, eq));
      return m.find() ? m.group(1) : null;
    } catch (IOException e) {
      return null;
    }
  }

  // Identifier passed to a find / a Selenium wait condition — `driver.findElement(loginButton)`,
  // `wait.until(visibilityOfElementLocated(usernameField))`, etc.
  private static final Pattern LOCATOR_ARG_RE = Pattern.compile(
      "\\b(?:findElement|findElements"
      + "|elementToBeClickable|visibilityOfElementLocated|presenceOfElementLocated"
      + "|presenceOfAllElementsLocatedBy|visibilityOfAllElementsLocatedBy|invisibilityOfElementLocated"
      + "|textToBePresentInElementLocated|frameToBeAvailableAndSwitchToIt"
      + "|numberOfElementsToBe(?:MoreThan|LessThan)?|attributeToBe|textToBe)"
      + "\\s*\\(\\s*(\\w+)\\s*[),]");

  // Action called directly on a locator/element variable — `loginButton.click()`, PageFactory fields.
  private static final Pattern ELEMENT_OP_RE = Pattern.compile(
      "\\b(\\w+)\\.(?:click|sendKeys|clear|submit|getText|getAttribute|getDomAttribute|isDisplayed"
      + "|isEnabled|isSelected|selectByVisibleText|selectByValue|selectByIndex|selectByContainsVisibleText)\\s*\\(");

  private static final Set<String> NON_LOCATOR_IDENTS = Set.of(
      "driver", "wait", "webdriver", "js", "actions", "select", "d", "w",
      "by", "locator", "loc", "selector", "element", "elem", "el", "webelement", "target");

  /** When the operation line references a locator/element by a bare identifier rather than
   *  inlining it (`findElement(loginButton)`, `loginButton.click()`, a wait condition), returns
   *  that identifier — decoded downstream the same way a {@code X = ...} assignment name is. */
  public static String extractLocatorReference(String sourceLocation) {
    String line = sourceLine(sourceLocation);
    if (line == null) {
      return null;
    }
    Matcher m = LOCATOR_ARG_RE.matcher(line);
    if (m.find() && !NON_LOCATOR_IDENTS.contains(m.group(1).toLowerCase())) {
      return m.group(1);
    }
    Matcher m2 = ELEMENT_OP_RE.matcher(line);
    if (m2.find() && !NON_LOCATOR_IDENTS.contains(m2.group(1).toLowerCase())) {
      return m2.group(1);
    }
    return null;
  }

  // An identifier in argument position: bracketed by `(` or `,` on the left, `)` or `,` on the
  // right. Handles nesting — `presenceOfElementLocated(txtUser)`, `click(driver, loginBtn)`,
  // `getElement(Txt_FirstName)`.
  private static final Pattern ARG_IDENTIFIER = Pattern.compile("[(,]\\s*([A-Za-z_]\\w*)\\s*(?=[),])");
  private static final Set<String> ARG_LITERALS = Set.of("true", "false", "null", "this");

  /** When the call site passes a locator by a bare identifier — {@code WebUtil.click(driver, loginButton)},
   *  {@code getElement(Txt_FirstName)}, {@code wait.until(presenceOfElementLocated(txtUser))} — returns
   *  it (the first argument that reads as a locator name). */
  public static String extractArgIdentifier(String sourceLocation) {
    String line = sourceLine(sourceLocation);
    if (line == null) {
      return null;
    }
    Matcher m = ARG_IDENTIFIER.matcher(line);
    while (m.find()) {
      String a = m.group(1);
      if (!ARG_LITERALS.contains(a.toLowerCase()) && !NON_LOCATOR_IDENTS.contains(a.toLowerCase())
          && looksLikeLocatorName(a)) {
        return a;
      }
    }
    return null;
  }

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");

  /** Last-resort: the first token anywhere on the line that reads as a UI locator name — catches
   *  {@code LoginPage.txtUserName.enterText("admin")} (enum-driven), a field/accessor
   *  {@code loginPage.usernameField().sendKeys(...)} (fluent chains), etc. {@code looksLikeLocatorName}
   *  is the gate, so assertion content, {@code driver}/{@code wait}, and plain method names like
   *  {@code doLogin} / {@code process} don't match. Runs only after the more specific extractors. */
  public static String extractLocatorishToken(String sourceLocation) {
    String line = sourceLine(sourceLocation);
    if (line == null) {
      return null;
    }
    Matcher m = IDENTIFIER.matcher(line);
    while (m.find()) {
      String id = m.group();
      if (!NON_LOCATOR_IDENTS.contains(id.toLowerCase()) && looksLikeLocatorName(id)) {
        return id;
      }
    }
    return null;
  }

  // Accessor / plumbing method names that end in a locator-ish word but aren't locators.
  private static final Set<String> METHOD_NAME_NOISE = Set.of(
      "getelement", "getelements", "findelement", "findelements", "webelement",
      "getlocator", "bylocator", "getby");

  /** A name that reads as a UI locator — a decode with a type hint ({@code loginButton},
   *  {@code txtUsername}), or an explicit {@code *Locator} / {@code *Selector} / {@code *By} suffix
   *  (but not an accessor method name like {@code getElement}). */
  private static boolean looksLikeLocatorName(String id) {
    String lc = id.toLowerCase();
    if (METHOD_NAME_NOISE.contains(lc)) {
      return false;
    }
    if ((lc.endsWith("locator") || lc.endsWith("selector") || lc.endsWith("by")) && lc.length() > 3) {
      return true;
    }
    Decoded d = decodeVariableName(id);
    return d != null && d.typeHint() != null;
  }

  // ---- assertion / negative-find context -------------------------------

  private static final Pattern ASSERTION_LINE = Pattern.compile(
      "\\b(assert\\w*|verif(?:y|ies)\\w*|assertThat|expect|should\\w*|checkThat)\\s*\\(",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern NEGATIVE_LINE = Pattern.compile(
      "\\b(?:invisibilityOf\\w*|stalenessOf|numberOfElementsToBeLessThan"
      + "|isNotDisplayed|isNotPresent|shouldNotBe\\w*|toBeGone|toDisappear|toBeRemoved)\\b"
      + "|(?:absent|notpresent|not_present|isgone|hasdisappeared|isremoved)"          // helper-name substrings
      + "|assert\\w*\\s*\\(\\s*(?:NoSuchElementException|TimeoutException)\\.class"
      + "|expect\\w*\\([^)]*(?:NoSuchElementException|TimeoutException)",
      Pattern.CASE_INSENSITIVE);

  /** The consumer's call-site line looks like an assertion (`assertEquals(... findElement ...)`,
   *  `assertThat(...)`, a `verify*` helper). */
  public static boolean isAssertionCallSite(String sourceLocation) {
    return isAssertionLine(sourceLine(sourceLocation));
  }

  static boolean isAssertionLine(String line) {
    return line != null && ASSERTION_LINE.matcher(line).find();
  }

  static boolean isNegativeLine(String line) {
    return line != null && NEGATIVE_LINE.matcher(line).find();
  }

  /** The find is expected to <em>fail</em> — an invisibility/staleness wait, an "assert absent"
   *  check, or {@code assertThrows(NoSuchElementException.class, ...)}. Healing here would defeat
   *  the assertion, so it's skipped entirely regardless of {@code HEALER_ASSERTIONS}. */
  public static boolean isNegativeFindContext(String sourceLocation, Throwable callSite) {
    if (isNegativeLine(sourceLine(sourceLocation))) {
      return true;
    }
    if (callSite != null) {
      for (StackTraceElement f : callSite.getStackTrace()) {
        String cls = f.getClassName();
        String m = f.getMethodName();
        if (m == null) {
          continue;
        }
        String ml = m.toLowerCase();
        if (cls.startsWith("org.openqa.selenium.support.ui.")
            && (ml.contains("invisibilit") || ml.contains("stalenessof")
                || ml.contains("numberofelementstobelessthan"))) {
          return true;
        }
        if (cls.contains("Assert")
            && (ml.contains("absent") || ml.contains("notpresent") || ml.contains("invisible")
                || ml.contains("disappear") || ml.contains("gone"))) {
          return true;
        }
      }
    }
    return false;
  }

  private static String sourceLine(String sourceLocation) {
    if (sourceLocation == null) {
      return null;
    }
    int sep = sourceLocation.lastIndexOf(':');
    if (sep == -1) {
      return null;
    }
    try {
      int lineNumber = Integer.parseInt(sourceLocation.substring(sep + 1));
      List<String> lines = Files.readAllLines(
          Path.of("").toAbsolutePath().resolve(sourceLocation.substring(0, sep)), StandardCharsets.UTF_8);
      return (lineNumber >= 1 && lineNumber <= lines.size()) ? lines.get(lineNumber - 1) : null;
    } catch (NumberFormatException | IOException e) {
      return null;
    }
  }

  /** String-aware {@code =} finder — ignores {@code =} inside quotes and skips {@code == != <= >= =>}. */
  static int findAssignmentEquals(String line) {
    char inString = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (inString != 0) {
        if (ch == '\\') {
          i++;
          continue;
        }
        if (ch == inString) {
          inString = 0;
        }
        continue;
      }
      if (ch == '"' || ch == '\'' || ch == '`') {
        inString = ch;
        continue;
      }
      if (ch != '=') {
        continue;
      }
      char prev = i > 0 ? line.charAt(i - 1) : 0;
      char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;
      if (next == '=' || prev == '=' || prev == '!' || prev == '<' || prev == '>' || next == '>') {
        continue;
      }
      return i;
    }
    return -1;
  }

  // ---- decodeVariableName -------------------------------------

  private static final Map<String, String> TYPE_PREFIXES = orderedMap(
      "txt", "textbox", "btn", "button", "chk", "checkbox", "cb", "checkbox",
      "rdo", "radio button", "ddl", "dropdown", "lnk", "link", "img", "image", "lbl", "label");

  private static final Map<String, String> TYPE_SUFFIXES = orderedMap(
      "radiobutton", "radio button", "checkbox", "checkbox", "textarea", "textarea", "textbox", "textbox",
      "textfield", "textbox", "dropdown", "dropdown", "combobox", "dropdown", "button", "button",
      "select", "dropdown", "input", "textbox", "field", "textbox", "radio", "radio button",
      "link", "link", "image", "image", "label", "label",
      "btn", "button", "chk", "checkbox", "cb", "checkbox", "rdo", "radio button",
      "ddl", "dropdown", "lnk", "link", "img", "image", "lbl", "label");

  private static final Set<String> MEANINGLESS_WORDS = Set.of(
      "el", "elem", "element", "obj", "val", "value", "loc", "locator", "ctrl", "control",
      "item", "temp", "tmp", "thing", "x", "y", "a", "b", "field", "box", "node");

  private static final Set<String> AFFIX_WORDS;
  static {
    AFFIX_WORDS = new java.util.HashSet<>();
    AFFIX_WORDS.addAll(TYPE_PREFIXES.keySet());
    AFFIX_WORDS.addAll(TYPE_SUFFIXES.keySet());
  }

  public record Decoded(String name, String typeHint) {}

  public static Decoded decodeVariableName(String raw) {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    String lower = raw.toLowerCase();
    String remainder = raw;
    String typeHint = null;

    for (Map.Entry<String, String> e : TYPE_PREFIXES.entrySet()) {
      String prefix = e.getKey();
      if (lower.startsWith(prefix) && raw.length() > prefix.length()
          && String.valueOf(raw.charAt(prefix.length())).matches("[A-Z_-]")) {
        remainder = raw.substring(prefix.length());
        typeHint = e.getValue();
        break;
      }
    }
    if (typeHint == null) {
      for (Map.Entry<String, String> e : TYPE_SUFFIXES.entrySet()) {
        String suffix = e.getKey();
        if (lower.endsWith(suffix) && raw.length() > suffix.length()) {
          remainder = raw.substring(0, raw.length() - suffix.length());
          typeHint = e.getValue();
          break;
        }
      }
    }

    List<String> words = splitIdentifierIntoWords(remainder);
    if (words.isEmpty()) {
      return null;
    }
    boolean allMeaningless = words.stream().allMatch(w -> {
      String bare = w.toLowerCase().replaceAll("\\d+$", "");
      return MEANINGLESS_WORDS.contains(bare) || AFFIX_WORDS.contains(bare);
    });
    if (allMeaningless) {
      return null;
    }
    StringBuilder name = new StringBuilder();
    for (String w : words) {
      if (name.length() > 0) {
        name.append(' ');
      }
      name.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
    }
    return new Decoded(name.toString(), typeHint);
  }

  static List<String> splitIdentifierIntoWords(String identifier) {
    String text = identifier
        .replaceAll("[_-]+", " ")
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
        .trim();
    if (text.isEmpty()) {
      return List.of();
    }
    return List.of(text.split("\\s+"));
  }

  private static Map<String, String> orderedMap(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.putIfAbsent(kv[i], kv[i + 1]);
    }
    return m;
  }
}
