package com.vibetestq.qtpsudhakar.tamash.healer;

import com.vibetestq.qtpsudhakar.tamash.healer.providers.AiSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HealLogTest {

  @Test
  void parseSourceLocation_splitsOnLastColon() {
    var loc = HealLog.parseSourceLocation("src/test/java/com/foo/LoginTest.java:42");
    assertNotNull(loc);
    assertEquals("src/test/java/com/foo/LoginTest.java", loc.file());
    assertEquals(42, loc.line());
    assertNull(HealLog.parseSourceLocation("no-colon-here"));
  }

  @Test
  void appendReadRoundTrip(@TempDir Path cwd) {
    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = "2026-08-28T10:00:00Z";
    e.file = "src/test/java/com/foo/LoginTest.java";
    e.line = 10;
    e.action = "sendKeys";
    e.description = "Username Textbox";
    e.suggestion = AiSuggestion.css("input[name='username']");
    e.newLocator = "By.cssSelector(\"input[name='username']\")";
    e.newFindBy = "@FindBy(css = \"input[name='username']\")";
    e.testSelector = "com.foo.LoginTest#logsIn";
    HealLog.appendHealLogEntry(e, cwd);

    List<HealLog.Entry> read = HealLog.readHealLog(cwd);
    assertEquals(1, read.size());
    assertEquals("sendKeys", read.get(0).action);
    assertEquals("css", read.get(0).suggestion.getStrategy());
    assertEquals("input[name='username']", read.get(0).suggestion.getCss());
    assertEquals("By.cssSelector(\"input[name='username']\")", read.get(0).newLocator);
    assertEquals("@FindBy(css = \"input[name='username']\")", read.get(0).newFindBy);
    assertEquals("com.foo.LoginTest#logsIn", read.get(0).testSelector);
  }

  @Test
  void findCachedSuggestion_prefersNewestWithSuggestion(@TempDir Path cwd) {
    HealLog.Entry older = entry("2026-08-28T09:00:00Z", 10, AiSuggestion.css("#a"));
    HealLog.Entry auditOnly = entry("2026-08-28T11:00:00Z", 10, null);
    HealLog.Entry newer = entry("2026-08-28T10:00:00Z", 10, AiSuggestion.css("#b"));
    HealLog.appendHealLogEntry(older, cwd);
    HealLog.appendHealLogEntry(auditOnly, cwd);
    HealLog.appendHealLogEntry(newer, cwd);

    var cached = HealLog.findCachedSuggestion("src/x.java:10", cwd);
    assertNotNull(cached);
    assertEquals("#b", cached.suggestion().getCss()); // newest that actually has a suggestion
  }

  @Test
  void readHealLogsFromDir_mergesShards(@TempDir Path root) throws Exception {
    Path shard1 = root.resolve("shard-1");
    Path shard2 = root.resolve("shard-2");
    HealLog.appendHealLogEntry(entry("t", 1, AiSuggestion.css("#a")), shard1);
    HealLog.appendHealLogEntry(entry("t", 2, AiSuggestion.css("#b")), shard2);
    List<HealLog.Entry> merged = HealLog.readHealLogsFromDir(root);
    assertEquals(2, merged.size());
  }

  @Test
  void clearAndArchive(@TempDir Path cwd) throws Exception {
    HealLog.appendHealLogEntry(entry("t", 1, AiSuggestion.css("#a")), cwd);
    HealLog.archiveHealLog("run-1", cwd);
    HealLog.clearHealLog(cwd);
    assertTrue(HealLog.readHealLog(cwd).isEmpty());
    assertTrue(Files.exists(cwd.resolve(".tamash-selenium/history/run-1.heals.jsonl")));
  }

  private static HealLog.Entry entry(String ts, int line, AiSuggestion s) {
    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = ts;
    e.file = "src/x.java";
    e.line = line;
    e.action = "click";
    e.suggestion = s;
    if (s == null) {
      e.reviewNote = "one-shot ref heal";
    }
    return e;
  }
}
