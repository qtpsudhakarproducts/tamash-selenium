package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.healer.providers.AiSuggestion;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of src/healer/heal-log.ts — an append-only JSONL trail of eligible heals under
 * {@code .tamash-selenium/heals.jsonl}, reused as an in-run/cross-run cache and consumed by
 * {@code apply-heals}. Every method is best-effort: a logging failure must never break a test run.
 */
public final class HealLog {
  private HealLog() {}

  private static final String LOG_DIR = ".tamash-selenium";
  private static final String LOG_FILE = "heals.jsonl";

  public static final class Entry {
    public String timestamp;
    public String file;
    public int line;
    public String action;
    public String description;
    public AiSuggestion suggestion; // absent for a one-shot ref/vision heal
    public String newLocator;       // ready-to-read "By.name(\"firstName\")" (rendered from suggestion)
    public String newFindBy;        // "@FindBy(name = \"firstName\")" equivalent, for PageFactory fields
    public String declarationLocation; // "path:line" of the `By field = By.x(...)` declaration, when the
                                       // call site referenced a field (no literal to rewrite there)
    public String testSelector;     // "com.foo.LoginTest#logsIn" for `mvn test -Dtest=...`
    public String testTitle;
    public boolean usedCache;
    public String initialSelector;
    public Boolean needsReview;
    public String reviewNote;

    JSONObject toJson() {
      JSONObject o = new JSONObject();
      o.put("timestamp", timestamp);
      o.put("file", file);
      o.put("line", line);
      o.put("action", action);
      if (description != null) o.put("description", description);
      if (suggestion != null) o.put("suggestion", suggestion.toJson());
      if (newLocator != null) o.put("newLocator", newLocator);
      if (newFindBy != null) o.put("newFindBy", newFindBy);
      if (declarationLocation != null) o.put("declarationLocation", declarationLocation);
      if (testSelector != null) o.put("testSelector", testSelector);
      if (testTitle != null) o.put("testTitle", testTitle);
      if (usedCache) o.put("usedCache", true);
      if (initialSelector != null) o.put("initialSelector", initialSelector);
      if (needsReview != null) o.put("needsReview", needsReview);
      if (reviewNote != null) o.put("reviewNote", reviewNote);
      return o;
    }

    static Entry fromJson(JSONObject o) {
      Entry e = new Entry();
      e.timestamp = o.optString("timestamp", null);
      e.file = o.optString("file", null);
      e.line = o.optInt("line", 0);
      e.action = o.optString("action", null);
      e.description = o.has("description") ? o.optString("description") : null;
      e.suggestion = o.has("suggestion") ? AiSuggestion.fromJson(o.optJSONObject("suggestion")) : null;
      e.newLocator = o.has("newLocator") ? o.optString("newLocator") : null;
      e.newFindBy = o.has("newFindBy") ? o.optString("newFindBy") : null;
      e.declarationLocation = o.has("declarationLocation") ? o.optString("declarationLocation") : null;
      e.testSelector = o.has("testSelector") ? o.optString("testSelector") : null;
      e.testTitle = o.has("testTitle") ? o.optString("testTitle") : null;
      e.usedCache = o.optBoolean("usedCache", false);
      e.initialSelector = o.has("initialSelector") ? o.optString("initialSelector") : null;
      e.needsReview = o.has("needsReview") ? o.optBoolean("needsReview") : null;
      e.reviewNote = o.has("reviewNote") ? o.optString("reviewNote") : null;
      return e;
    }
  }

  public record SourceLocation(String file, int line) {}

  /** {@code sourceLocation} is always "file:line" — relative paths never contain a colon (drive
   *  letters are already stripped), so split on the LAST colon. */
  public static SourceLocation parseSourceLocation(String sourceLocation) {
    if (sourceLocation == null) {
      return null;
    }
    int sep = sourceLocation.lastIndexOf(':');
    if (sep == -1) {
      return null;
    }
    try {
      return new SourceLocation(sourceLocation.substring(0, sep), Integer.parseInt(sourceLocation.substring(sep + 1)));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Path logPath(Path cwd) {
    return cwd.resolve(LOG_DIR).resolve(LOG_FILE);
  }

  public static Path cwd() {
    return Path.of("").toAbsolutePath();
  }

  public static void appendHealLogEntry(Entry entry) {
    appendHealLogEntry(entry, cwd());
  }

  public static void appendHealLogEntry(Entry entry, Path cwd) {
    try {
      Path dir = cwd.resolve(LOG_DIR);
      Files.createDirectories(dir);
      Files.writeString(logPath(cwd), entry.toJson().toString() + "\n",
          StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      // best-effort — a convenience trail, never something a test's pass/fail depends on
    }
  }

  public static List<Entry> readHealLog() {
    return readHealLog(cwd());
  }

  public static List<Entry> readHealLog(Path cwd) {
    return parseHealLogFile(logPath(cwd));
  }

  private static List<Entry> parseHealLogFile(Path file) {
    List<Entry> entries = new ArrayList<>();
    if (!Files.isRegularFile(file)) {
      return entries;
    }
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        try {
          entries.add(Entry.fromJson(new JSONObject(line)));
        } catch (Exception ignored) {
          // a single corrupted line (run killed mid-write) shouldn't take down the whole log
        }
      }
    } catch (IOException ignored) {
      // best-effort
    }
    return entries;
  }

  /** Supports the sharded-CI pattern: reads every {@code heals.jsonl} found anywhere under {@code dir}. */
  public static List<Entry> readHealLogsFromDir(Path dir) {
    List<Entry> entries = new ArrayList<>();
    if (!Files.isDirectory(dir)) {
      return entries;
    }
    try (var stream = Files.walk(dir)) {
      stream.filter(p -> p.getFileName().toString().equals(LOG_FILE))
          .forEach(p -> entries.addAll(parseHealLogFile(p)));
    } catch (IOException ignored) {
      // best-effort
    }
    return entries;
  }

  public record Cached(AiSuggestion suggestion, String initialSelector, Boolean needsReview, String reviewNote) {}

  /** Before healActionFailure pays for a snapshot + AI call, tries the newest previously-confirmed
   *  suggestion for this exact source location. A stale entry just fails to replay. */
  public static Cached findCachedSuggestion(String sourceLocation) {
    return findCachedSuggestion(sourceLocation, cwd());
  }

  public static Cached findCachedSuggestion(String sourceLocation, Path cwd) {
    SourceLocation loc = parseSourceLocation(sourceLocation);
    if (loc == null) {
      return null;
    }
    Entry newest = null;
    for (Entry e : readHealLog(cwd)) {
      if (e.suggestion == null || !loc.file().equals(e.file) || loc.line() != e.line) {
        continue;
      }
      if (newest == null || (e.timestamp != null && e.timestamp.compareTo(newest.timestamp) > 0)) {
        newest = e;
      }
    }
    return newest == null ? null
        : new Cached(newest.suggestion, newest.initialSelector, newest.needsReview, newest.reviewNote);
  }

  private static Path historyDir(Path cwd) {
    return cwd.resolve(LOG_DIR).resolve("history");
  }

  public static void archiveHealLog(String label, Path cwd) {
    try {
      Path src = logPath(cwd);
      if (!Files.isRegularFile(src)) {
        return;
      }
      Files.createDirectories(historyDir(cwd));
      Files.copy(src, historyDir(cwd).resolve(label + ".heals.jsonl"));
    } catch (IOException ignored) {
      // best-effort
    }
  }

  public static void archiveMergedEntries(List<Entry> entries, String label, Path cwd) {
    if (entries.isEmpty()) {
      return;
    }
    try {
      Files.createDirectories(historyDir(cwd));
      StringBuilder sb = new StringBuilder();
      for (Entry e : entries) {
        sb.append(e.toJson()).append('\n');
      }
      Files.writeString(historyDir(cwd).resolve(label + ".heals.jsonl"), sb.toString(), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      // best-effort
    }
  }

  public static void clearHealLog(Path cwd) {
    try {
      Files.deleteIfExists(logPath(cwd));
    } catch (IOException ignored) {
      // best-effort
    }
  }
}
