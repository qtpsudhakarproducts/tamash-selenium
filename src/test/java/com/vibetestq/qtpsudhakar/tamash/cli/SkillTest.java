package com.vibetestq.qtpsudhakar.tamash.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Skill} backs {@code init-skill} and {@code doctor}'s Skill check. These do real installs
 * into a JUnit temp dir. Mirrors pw's {@code unit-tests/skill.test.js}.
 */
class SkillTest {

  private static final String V1 = "9.9.9-test.1";
  private static final String V2 = "9.9.9-test.2";

  private static Skill.InstallResult install(Path cwd, String id, String version, boolean force, boolean dryRun) {
    return Skill.installSkill(Skill.getTarget(id).orElseThrow(),
        new Skill.InstallOptions(cwd, version, false, force, dryRun));
  }

  // ---- targets ------------------------------------------------------

  @Test
  void theTwoTargetsAreClaudeAndAgents() {
    assertEquals(List.of("agents", "claude"),
        Skill.TARGETS.stream().map(Skill.TargetSpec::id).sorted().toList());
    assertEquals(Path.of(".claude", "skills", "tamash-selenium"),
        Skill.getTarget("claude").orElseThrow().projectDir());
    assertEquals(Path.of(".agents", "skills", "tamash-selenium"),
        Skill.getTarget("agents").orElseThrow().projectDir());
    assertTrue(Skill.getTarget("nope").isEmpty());
  }

  // ---- version marker --------------------------------------------

  @Test
  void readVersionMarker_extractsOrNull() {
    assertEquals("1.2.3", Skill.readVersionMarker("tamash-selenium-skill-version: 1.2.3\n"));
    assertNull(Skill.readVersionMarker("nothing here"));
    assertNull(Skill.readVersionMarker(null));
  }

  @Test
  void getPackageVersion_resolvesFromTheBuild() {
    assertTrue(Skill.getPackageVersion().matches("\\d+\\.\\d+\\.\\d+.*"),
        "got: " + Skill.getPackageVersion());
    assertTrue(Skill.skillResourceAvailable(), "skill files must be on the classpath");
  }

  // ---- install ---------------------------------------------------

  @Test
  void install_copiesSkillAndReferences_excludesAdapters_writesMarker(@TempDir Path cwd) throws IOException {
    Skill.InstallResult r = install(cwd, "agents", V1, false, false);
    assertEquals(Skill.Action.CREATED, r.action());

    Path base = cwd.resolve(Path.of(".agents", "skills", "tamash-selenium"));
    assertTrue(Files.exists(base.resolve("SKILL.md")));
    assertTrue(Files.exists(base.resolve("references/onboarding.md")));
    assertTrue(Files.exists(base.resolve("references/heal.md")));
    assertFalse(Files.exists(base.resolve("adapters")), "adapters/ must not be copied");
    assertEquals(V1, Skill.readVersionMarker(Files.readString(base.resolve(Skill.MARKER_FILENAME))));
  }

  @Test
  void install_isIdempotentForTheSameVersion(@TempDir Path cwd) {
    assertEquals(Skill.Action.CREATED, install(cwd, "claude", V1, false, false).action());
    assertEquals(Skill.Action.SKIPPED, install(cwd, "claude", V1, false, false).action());
  }

  @Test
  void install_reCopiesOnAVersionBump(@TempDir Path cwd) throws IOException {
    install(cwd, "claude", V1, false, false);
    Skill.InstallResult r = install(cwd, "claude", V2, false, false);
    assertEquals(Skill.Action.UPDATED, r.action());
    Path marker = cwd.resolve(Path.of(".claude", "skills", "tamash-selenium", Skill.MARKER_FILENAME));
    assertEquals(V2, Skill.readVersionMarker(Files.readString(marker)));
  }

  @Test
  void install_blocksAnUnmarkedDirUnlessForced(@TempDir Path cwd) throws IOException {
    Path base = cwd.resolve(Path.of(".agents", "skills", "tamash-selenium"));
    Files.createDirectories(base);
    Files.writeString(base.resolve("SKILL.md"), "# hand-written, do not clobber");

    Skill.InstallResult blocked = install(cwd, "agents", V1, false, false);
    assertEquals(Skill.Action.BLOCKED, blocked.action());
    assertEquals("# hand-written, do not clobber", Files.readString(base.resolve("SKILL.md")));

    Skill.InstallResult forced = install(cwd, "agents", V1, true, false);
    assertEquals(Skill.Action.UPDATED, forced.action());
    assertTrue(Files.readString(base.resolve("SKILL.md")).length() > 40);
    assertTrue(Files.exists(base.resolve(Skill.MARKER_FILENAME)));
  }

  @Test
  void dryRun_changesNothing(@TempDir Path cwd) {
    Skill.InstallResult r = install(cwd, "agents", V1, false, true);
    assertEquals(Skill.Action.CREATED, r.action());
    assertFalse(Files.exists(cwd.resolve(Path.of(".agents", "skills", "tamash-selenium", "SKILL.md"))));
  }

  // ---- state (doctor) -------------------------------------------

  @Test
  void skillState_absentThenCurrentThenOutdated(@TempDir Path cwd) {
    Skill.TargetSpec claude = Skill.getTarget("claude").orElseThrow();
    assertEquals(Skill.Status.ABSENT, Skill.skillState(cwd, claude, V1).status());

    install(cwd, "claude", V1, false, false);
    assertEquals(Skill.Status.CURRENT, Skill.skillState(cwd, claude, V1).status());

    Skill.SkillState stale = Skill.skillState(cwd, claude, V2);
    assertEquals(Skill.Status.OUTDATED, stale.status());
    assertEquals(V1, stale.installed());
    assertEquals(V2, stale.version());
  }

  @Test
  void skillState_unmanagedWhenNoMarker(@TempDir Path cwd) throws IOException {
    Path base = cwd.resolve(Path.of(".claude", "skills", "tamash-selenium"));
    Files.createDirectories(base);
    Files.writeString(base.resolve("SKILL.md"), "manual copy");
    assertEquals(Skill.Status.UNMANAGED,
        Skill.skillState(cwd, Skill.getTarget("claude").orElseThrow(), V1).status());
  }

  // ---- legacy artifacts ---------------------------------------

  @Test
  void legacyInstallArtifacts_findsAnAdaptersLeftover(@TempDir Path cwd) throws IOException {
    assertTrue(Skill.legacyInstallArtifacts(cwd).isEmpty());
    Files.createDirectories(cwd.resolve(Path.of(".claude", "skills", "tamash-selenium", "adapters")));
    assertTrue(Skill.legacyInstallArtifacts(cwd).stream().anyMatch(s -> s.contains("adapters")));
  }
}
