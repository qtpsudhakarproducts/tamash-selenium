package io.github.qtpsudhakarproducts.tamash.cli;

import io.github.qtpsudhakarproducts.tamash.healer.DurableLocator;
import io.github.qtpsudhakarproducts.tamash.healer.HealLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.qtpsudhakarproducts.tamash.cli.ConsoleStyle.*;

/**
 * Port of src/cli/applyHeals.ts — turns recorded runtime heals into permanent Java source edits,
 * plus JSON/Markdown reports and a verification script. Not a real Java parser: a small
 * string/paren-balance scanner anchored at the exact line captured at heal time.
 */
public final class ApplyHeals {
  private ApplyHeals() {}

  private static final String REPORT_DIR = ".tamash-selenium";
  private static final List<String> FACTORY_METHODS = List.of(
      "id", "name", "cssSelector", "xpath", "className", "tagName", "linkText", "partialLinkText");
  private static final Pattern FACTORY_METHOD_PATTERN =
      Pattern.compile("\\bBy\\.(" + String.join("|", FACTORY_METHODS) + ")\\(");

  public record FixOutcome(String file, int line, String before, String after, boolean applied,
                           String reason, Boolean needsReview, String reviewNote) {}

  record CallRange(int dotIndex, int callEnd) {}

  // ---- source scanning --------------------------------------------------

  static CallRange findFactoryCallOnLine(String content, int targetLine) {
    String[] lines = content.split("\n", -1);
    if (targetLine < 1 || targetLine > lines.length) {
      return null;
    }
    int lineStart = 0;
    for (int i = 0; i < targetLine - 1; i++) {
      lineStart += lines[i].length() + 1;
    }
    Matcher m = FACTORY_METHOD_PATTERN.matcher(lines[targetLine - 1]);
    if (!m.find()) {
      return null;
    }
    int dotIndex = lineStart + m.start();
    int openParen = dotIndex + m.group().length() - 1;
    Integer callEnd = scanBalancedParens(content, openParen);
    if (callEnd == null) {
      return null;
    }
    // The Selenium replacement is always a single `By.xxx("...")` expression — no chained
    // continuation to consume (unlike the Playwright port's generated `near` chain).
    return new CallRange(dotIndex, callEnd);
  }

  private static final Pattern FINDBY_PATTERN = Pattern.compile("@(FindBy|FindBys|FindAll)\\s*\\(");

  /** Locates a {@code @FindBy} / {@code @FindBys} / {@code @FindAll} annotation starting on
   *  {@code targetLine} (a PageFactory-field heal records the annotation's line). The paren scan
   *  spans lines, so a multi-line annotation is captured whole. */
  static CallRange findFindByAnnotationOnLine(String content, int targetLine) {
    String[] lines = content.split("\n", -1);
    if (targetLine < 1 || targetLine > lines.length) {
      return null;
    }
    int lineStart = 0;
    for (int i = 0; i < targetLine - 1; i++) {
      lineStart += lines[i].length() + 1;
    }
    Matcher m = FINDBY_PATTERN.matcher(lines[targetLine - 1]);
    if (!m.find()) {
      return null;
    }
    int annStart = lineStart + m.start();
    int openParen = lineStart + m.end() - 1;
    Integer annEnd = scanBalancedParens(content, openParen);
    return annEnd == null ? null : new CallRange(annStart, annEnd);
  }

  static boolean isCompositeFindBy(String annotationText) {
    return annotationText.startsWith("@FindBys") || annotationText.startsWith("@FindAll");
  }

  private static Integer scanBalancedParens(String content, int openParenIndex) {
    int depth = 1;
    int i = openParenIndex + 1;
    char inString = 0;
    while (i < content.length() && depth > 0) {
      char ch = content.charAt(i);
      if (inString != 0) {
        if (ch == '\\') {
          i += 2;
          continue;
        }
        if (ch == inString) {
          inString = 0;
        }
      } else if (ch == '"' || ch == '\'') {
        inString = ch;
      } else if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
      }
      i++;
    }
    return depth == 0 ? i : null;
  }

  // ---- planning -------------------------------------------------------

  private static List<HealLog.Entry> latestPerLocation(List<HealLog.Entry> entries) {
    Map<String, HealLog.Entry> byKey = new LinkedHashMap<>();
    for (HealLog.Entry e : entries) {
      String key = e.file + ":" + e.line;
      HealLog.Entry existing = byKey.get(key);
      if (existing == null || isBetterCandidate(e, existing)) {
        byKey.put(key, e);
      }
    }
    return new ArrayList<>(byKey.values());
  }

  private static boolean isBetterCandidate(HealLog.Entry candidate, HealLog.Entry current) {
    boolean cu = candidate.suggestion != null;
    boolean ru = current.suggestion != null;
    if (cu != ru) {
      return cu;
    }
    return candidate.timestamp != null && current.timestamp != null
        && candidate.timestamp.compareTo(current.timestamp) > 0;
  }

  public record Plan(List<FixOutcome> outcomes, Map<Path, String> fileContents, List<String> affectedTests) {}

  public static Plan planFixes(Path cwd, List<HealLog.Entry> rawEntries) {
    List<HealLog.Entry> all = rawEntries != null ? rawEntries : HealLog.readHealLog(cwd);
    List<HealLog.Entry> entries = latestPerLocation(all);

    Map<String, List<HealLog.Entry>> byFile = new LinkedHashMap<>();
    for (HealLog.Entry e : entries) {
      byFile.computeIfAbsent(e.file, k -> new ArrayList<>()).add(e);
    }

    List<FixOutcome> outcomes = new ArrayList<>();
    Map<Path, String> fileContents = new LinkedHashMap<>();

    for (Map.Entry<String, List<HealLog.Entry>> fe : byFile.entrySet()) {
      String relativeFile = fe.getKey();
      Path fullPath = cwd.resolve(relativeFile);
      if (!Files.isRegularFile(fullPath)) {
        for (HealLog.Entry e : fe.getValue()) {
          outcomes.add(new FixOutcome(relativeFile, e.line, "", "", false, "File no longer exists.", null, null));
        }
        continue;
      }
      String content;
      try {
        content = Files.readString(fullPath, StandardCharsets.UTF_8);
      } catch (IOException io) {
        for (HealLog.Entry e : fe.getValue()) {
          outcomes.add(new FixOutcome(relativeFile, e.line, "", "", false, "Could not read file.", null, null));
        }
        continue;
      }
      boolean changed = false;
      // The line each entry will actually be rewritten at: the call site, unless it references a
      // `By` field (no By.x(...) literal there) and the declaration is in this same file.
      Map<HealLog.Entry, Integer> targetLines = new LinkedHashMap<>();
      for (HealLog.Entry e : fe.getValue()) {
        int t = e.line;
        if (e.declarationLocation != null && findFactoryCallOnLine(content, e.line) == null) {
          int sep = e.declarationLocation.lastIndexOf(':');
          if (sep != -1 && e.declarationLocation.substring(0, sep).replace('\\', '/').endsWith(relativeFile)) {
            try {
              t = Integer.parseInt(e.declarationLocation.substring(sep + 1));
            } catch (NumberFormatException ignored) {
              // keep e.line
            }
          }
        }
        targetLines.put(e, t);
      }
      List<HealLog.Entry> sorted = new ArrayList<>(fe.getValue());
      sorted.sort((a, b) -> Integer.compare(targetLines.get(b), targetLines.get(a))); // bottom-to-top

      for (HealLog.Entry e : sorted) {
        if (e.suggestion == null) {
          outcomes.add(new FixOutcome(relativeFile, e.line, "", "", false,
              e.reviewNote != null ? e.reviewNote
                  : "This heal produced no durable selector to apply (a one-shot element reference).",
              null, null));
          continue;
        }
        int targetLine = targetLines.get(e);
        CallRange range = findFactoryCallOnLine(content, targetLine);
        String replacement = range != null ? DurableLocator.generateReplacementCall(e.suggestion) : null;
        boolean annotation = false;
        Boolean needsReview = e.needsReview;
        String reviewNote = e.reviewNote;

        if (range == null) {
          // Not a By.xxx(...) call — try a @FindBy / @FindBys / @FindAll annotation (PageFactory).
          range = findFindByAnnotationOnLine(content, e.line);
          if (range == null && targetLine != e.line) {
            range = findFindByAnnotationOnLine(content, targetLine);
          }
          if (range != null) {
            annotation = true;
            replacement = DurableLocator.generateFindByAnnotation(e.suggestion);
            if (replacement != null && isCompositeFindBy(content.substring(range.dotIndex(), range.callEnd()))) {
              needsReview = Boolean.TRUE;
              reviewNote = "Replaced a @FindBys/@FindAll composite with a single @FindBy — confirm the semantics.";
            }
          }
        }

        if (range == null || replacement == null) {
          String reason;
          if (range == null) {
            reason = "Could not find the original locator call or @FindBy annotation on this line — the file may have changed.";
          } else if (annotation) {
            reason = "The healed selector can't be expressed as a @FindBy attribute.";
          } else {
            reason = "Unsupported suggestion strategy \"" + e.suggestion.getStrategy() + "\".";
          }
          outcomes.add(new FixOutcome(relativeFile, targetLine, "", "", false, reason, null, null));
          continue;
        }
        String before = content.substring(range.dotIndex(), range.callEnd());
        String after = replacement;
        content = content.substring(0, range.dotIndex()) + after + content.substring(range.callEnd());
        changed = true;
        outcomes.add(new FixOutcome(relativeFile, targetLine, before, after, true, null, needsReview, reviewNote));
      }
      if (changed) {
        fileContents.put(fullPath, content);
      }
    }

    // affected tests: distinct testSelector for every location that actually got fixed
    var appliedLocations = outcomes.stream().filter(FixOutcome::applied)
        .map(o -> o.file() + ":" + o.line()).collect(Collectors.toSet());
    TreeSet<String> affected = new TreeSet<>();
    for (HealLog.Entry e : all) {
      if (e.testSelector != null && appliedLocations.contains(e.file + ":" + e.line)) {
        affected.add(e.testSelector);
      }
    }
    return new Plan(outcomes, fileContents, new ArrayList<>(affected));
  }

  // ---- reports & verification script -------------------------------

  private static String timestampLabel() {
    return Instant.now().toString().replaceAll("[:.]", "-");
  }

  static String writeVerificationScript(List<String> affectedTests, Path cwd) throws IOException {
    if (affectedTests.isEmpty()) {
      return null;
    }
    Path dir = cwd.resolve(REPORT_DIR);
    Files.createDirectories(dir);
    String testArg = String.join(",", affectedTests);
    String sh = "#!/usr/bin/env sh\n"
        + "# Auto-generated by `tamash-selenium apply-heals` — re-runs exactly the tests affected by\n"
        + "# the most recent run, with self-healing disabled, so a pass proves the rewritten selectors\n"
        + "# stand alone.\n"
        + "cd \"$(dirname \"$0\")/..\"\n"
        + "exec mvn -q test -DHEALER_ENABLED=false -Dtest='" + testArg + "'\n";
    Path shPath = dir.resolve("verify-heals.sh");
    Files.writeString(shPath, sh, StandardCharsets.UTF_8);
    String cmd = "@echo off\r\n"
        + "cd /d \"%~dp0..\"\r\n"
        + "mvn -q test -DHEALER_ENABLED=false -Dtest=\"" + testArg + "\"\r\n";
    Files.writeString(dir.resolve("verify-heals.cmd"), cmd, StandardCharsets.UTF_8);
    return cwd.relativize(shPath).toString().replace('\\', '/');
  }

  static Path writeReports(List<FixOutcome> outcomes, List<String> affectedTests, boolean dryRun, Path cwd,
                           String label, String verifyScriptPath) throws IOException {
    Path dir = cwd.resolve(REPORT_DIR);
    Path historyDir = dir.resolve("history");
    Files.createDirectories(historyDir);

    List<FixOutcome> applied = outcomes.stream().filter(FixOutcome::applied).toList();
    List<FixOutcome> skipped = outcomes.stream().filter(o -> !o.applied()).toList();
    String timestamp = Instant.now().toString();

    org.json.JSONObject json = new org.json.JSONObject();
    json.put("timestamp", timestamp);
    json.put("dryRun", dryRun);
    json.put("applied", applied.size());
    json.put("skipped", skipped.size());
    org.json.JSONArray fixes = new org.json.JSONArray();
    for (FixOutcome o : outcomes) {
      org.json.JSONObject f = new org.json.JSONObject();
      f.put("file", o.file()); f.put("line", o.line());
      f.put("before", o.before()); f.put("after", o.after()); f.put("applied", o.applied());
      if (o.reason() != null) f.put("reason", o.reason());
      if (o.needsReview() != null) f.put("needsReview", o.needsReview());
      if (o.reviewNote() != null) f.put("reviewNote", o.reviewNote());
      fixes.put(f);
    }
    json.put("fixes", fixes);
    json.put("affectedTests", new org.json.JSONArray(affectedTests));
    if (verifyScriptPath != null) json.put("verifyCommand", "sh " + verifyScriptPath);
    String jsonContent = json.toString(2);
    Files.writeString(dir.resolve("apply-heals-report.json"), jsonContent, StandardCharsets.UTF_8);
    Files.writeString(historyDir.resolve(label + ".apply-heals-report.json"), jsonContent, StandardCharsets.UTF_8);

    StringBuilder md = new StringBuilder("# Self-healing fixes\n\n");
    md.append("Generated ").append(timestamp).append(" by `tamash-selenium apply-heals`")
        .append(dryRun ? " (--dry-run, nothing written)" : "").append(".\n\n");
    if (!applied.isEmpty()) {
      long nr = applied.stream().filter(f -> Boolean.TRUE.equals(f.needsReview())).count();
      md.append("## Applied (").append(applied.size()).append(nr > 0 ? ", " + nr + " needing review" : "").append(")\n\n");
      for (FixOutcome f : applied) {
        md.append("### ").append(Boolean.TRUE.equals(f.needsReview()) ? "⚠️ " : "")
            .append("`").append(f.file()).append(':').append(f.line()).append("`\n\n")
            .append("**Before:**\n```java\n").append(f.before()).append("\n```\n\n")
            .append("**After:**\n```java\n").append(f.after()).append("\n```\n\n");
        if (Boolean.TRUE.equals(f.needsReview())) {
          md.append("> ⚠️ **Needs review:** ").append(f.reviewNote() != null ? f.reviewNote() : "double-check this selector.").append("\n\n");
        }
      }
    }
    if (!skipped.isEmpty()) {
      md.append("## Skipped (").append(skipped.size()).append(")\n\n");
      for (FixOutcome f : skipped) {
        md.append("- `").append(f.file()).append(':').append(f.line()).append("` — ").append(f.reason()).append('\n');
      }
      md.append('\n');
    }
    if (!affectedTests.isEmpty()) {
      md.append("## Tests to re-verify\n\n");
      for (String t : affectedTests) {
        md.append("- `").append(t).append("`\n");
      }
      md.append('\n');
      if (verifyScriptPath != null) {
        md.append("```bash\nsh ").append(verifyScriptPath).append("\n```\n\n");
      }
    }
    Path mdPath = dir.resolve("apply-heals-report.md");
    Files.writeString(mdPath, md.toString(), StandardCharsets.UTF_8);
    Files.writeString(historyDir.resolve(label + ".apply-heals-report.md"), md.toString(), StandardCharsets.UTF_8);
    return mdPath;
  }

  // ---- CLI entry --------------------------------------------------

  public static void run(String[] args) throws IOException {
    List<String> a = List.of(args);
    boolean dryRun = a.contains("--dry-run");
    boolean skipConfirm = a.contains("--yes") || a.contains("-y");
    String logsDir = null;
    int li = a.indexOf("--logs-dir");
    if (li != -1 && li + 1 < a.size()) {
      logsDir = a.get(li + 1);
    }

    System.out.println(bold("tamash-selenium apply-heals"));
    Path cwd = Path.of("").toAbsolutePath();
    List<HealLog.Entry> rawEntries = logsDir != null ? HealLog.readHealLogsFromDir(cwd.resolve(logsDir)) : null;
    Plan plan = planFixes(cwd, rawEntries);

    if (plan.outcomes().isEmpty()) {
      System.out.println(logsDir != null ? "No eligible heals found under " + logsDir + "."
          : "No eligible heals found in .tamash-selenium/heals.jsonl. (Run your tests first.)");
      return;
    }

    List<FixOutcome> applied = printOutcomeTables(plan.outcomes(), dryRun);
    long skipped = plan.outcomes().size() - applied.size();

    if (!dryRun && isInteractive() && !skipConfirm) {
      long nr = applied.stream().filter(o -> Boolean.TRUE.equals(o.needsReview())).count();
      String suffix = nr > 0 ? " (" + nr + " needing review)" : "";
      if (!confirm("\nApply " + applied.size() + " fix(es) to your source files" + suffix + "? [y/N]: ")) {
        System.out.println("Aborted — no changes written.");
        return;
      }
    }

    String label = timestampLabel();
    String verifyScriptPath = dryRun ? null : writeVerificationScript(plan.affectedTests(), cwd);
    Path mdPath = writeReports(plan.outcomes(), plan.affectedTests(), dryRun, cwd, label, verifyScriptPath);
    System.out.println("\nReport written to " + cwd.relativize(mdPath) + " (and the matching .json).");
    System.out.println(plan.affectedTests().isEmpty()
        ? "Tests to re-verify: (none recorded — re-run the full suite to be safe.)"
        : "Tests to re-verify: " + String.join(" ", plan.affectedTests()));

    if (dryRun) {
      System.out.println(applied.size() + " fix(es) would be applied, " + skipped + " skipped. Re-run without --dry-run to write them.");
      return;
    }
    List<HealLog.Entry> merged = rawEntries;
    for (Map.Entry<Path, String> fc : plan.fileContents().entrySet()) {
      Files.writeString(fc.getKey(), fc.getValue(), StandardCharsets.UTF_8);
    }
    System.out.println(applied.size() + " fix(es) applied to " + plan.fileContents().size() + " file(s), " + skipped + " skipped.");
    System.out.println("Review the changes (e.g. `git diff`) before committing.");
    if (verifyScriptPath != null) {
      System.out.println("Verification: sh " + verifyScriptPath + "  (runs the affected tests with HEALER_ENABLED=false)");
    }

    if (logsDir != null) {
      HealLog.archiveMergedEntries(merged != null ? merged : List.of(), label, cwd);
    } else {
      HealLog.archiveHealLog(label, cwd);
    }
    HealLog.clearHealLog(cwd);
  }

  private static List<FixOutcome> printOutcomeTables(List<FixOutcome> outcomes, boolean dryRun) {
    List<FixOutcome> applied = outcomes.stream().filter(FixOutcome::applied).toList();
    List<FixOutcome> skipped = outcomes.stream().filter(o -> !o.applied()).toList();

    if (!applied.isEmpty()) {
      long nr = applied.stream().filter(o -> Boolean.TRUE.equals(o.needsReview())).count();
      section((dryRun ? "Would fix (" : "Fixes (") + applied.size() + (nr > 0 ? ", " + nr + " needing review" : "") + ")");
      List<List<String>> rows = new ArrayList<>();
      for (FixOutcome o : applied) {
        rows.add(List.of(
            dim(truncateStart(o.file() + ":" + o.line(), 32)),
            truncateEnd(o.before(), 42),
            truncateEnd(o.after(), 42),
            Boolean.TRUE.equals(o.needsReview()) ? yellow("⚠ yes") : dim("—")));
      }
      renderTable(List.of("Location", "Before", "After", "Review"), rows);
      for (FixOutcome o : applied) {
        if (Boolean.TRUE.equals(o.needsReview())) {
          System.out.println("  " + yellow("⚠") + " " + dim(o.file() + ":" + o.line()) + " — "
              + (o.reviewNote() != null ? o.reviewNote() : "double-check this selector."));
        }
      }
    }
    if (!skipped.isEmpty()) {
      section("Skipped (" + skipped.size() + ")");
      List<List<String>> rows = new ArrayList<>();
      for (FixOutcome o : skipped) {
        rows.add(List.of(dim(truncateStart(o.file() + ":" + o.line(), 32)), truncateEnd(o.reason() == null ? "" : o.reason(), 78)));
      }
      renderTable(List.of("Location", "Reason"), rows);
    }
    return applied;
  }
}
