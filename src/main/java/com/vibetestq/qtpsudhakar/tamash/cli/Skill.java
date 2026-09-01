package com.vibetestq.qtpsudhakar.tamash.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backs {@code tamash-selenium init-skill} (copies the shipped orchestration skill into a project)
 * and {@code doctor}'s Skill check. Follows the convention Playwright's own
 * {@code playwright-cli install --skills} established: the same {@code SKILL.md} + {@code references/}
 * goes into <b>both</b> standard locations, with no per-agent format conversion —
 * <pre>
 *   .claude/skills/tamash-selenium/   — Claude Code
 *   .agents/skills/tamash-selenium/   — the cross-tool standard (Cursor, Copilot, Windsurf, Kiro, …)
 * </pre>
 * The skill files ship inside the JAR under {@code /skills/tamash-selenium/}; this class copies
 * them out. A one-line version marker ({@value #MARKER_FILENAME}) is written into each install so
 * {@code doctor} can flag when it has fallen behind the package.
 */
public final class Skill {
  private Skill() {}

  static final String MARKER_FILENAME = ".tamash-selenium-skill";
  private static final String MARKER_PREFIX = "tamash-selenium-skill-version:";
  private static final Pattern MARKER_RE = Pattern.compile("tamash-selenium-skill-version:\\s*(\\S+)");

  private static final String RESOURCE_ROOT = "/skills/tamash-selenium";
  /** The files that make up the skill. {@code adapters/} is deliberately excluded — legacy. */
  static final List<String> SKILL_FILES = List.of(
      "SKILL.md", "references/onboarding.md", "references/heal.md");

  // ---- targets --------------------------------------------------------

  public record TargetSpec(String id, String label, Path projectDir, Path userDir) {}

  public static final List<TargetSpec> TARGETS = List.of(
      new TargetSpec("claude", "Claude Code (.claude/skills/)",
          Path.of(".claude", "skills", "tamash-selenium"),
          userHome().resolve(Path.of(".claude", "skills", "tamash-selenium"))),
      new TargetSpec("agents", "cross-tool standard (.agents/skills/)",
          Path.of(".agents", "skills", "tamash-selenium"),
          userHome().resolve(Path.of(".agents", "skills", "tamash-selenium"))));

  public static Optional<TargetSpec> getTarget(String id) {
    return TARGETS.stream().filter(t -> t.id().equals(id)).findFirst();
  }

  private static Path userHome() {
    return Path.of(System.getProperty("user.home", "."));
  }

  // ---- version marker ----------------------------------------------

  public static String versionMarker(String version) {
    return MARKER_PREFIX + " " + version;
  }

  /** The version recorded in a marker file's text, or null if it carries none. */
  public static String readVersionMarker(String text) {
    if (text == null) return null;
    Matcher m = MARKER_RE.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  // ---- package version -------------------------------------------

  /** The running package's version — from the JAR's Maven {@code pom.properties}, else the
   *  package implementation version, else a local {@code pom.xml}, else {@code "unknown"}. */
  public static String getPackageVersion() {
    for (String res : List.of(
        "/com/vibetestq/qtpsudhakar/tamash/build.properties",
        "/META-INF/maven/com.vibetestq.qtpsudhakar/tamash-selenium/pom.properties")) {
      try (InputStream in = Skill.class.getResourceAsStream(res)) {
        if (in != null) {
          java.util.Properties p = new java.util.Properties();
          p.load(in);
          String v = p.getProperty("version");
          if (v != null && !v.isBlank() && !v.contains("${")) return v.trim();
        }
      } catch (IOException ignored) {
        // try the next candidate
      }
    }
    String impl = Skill.class.getPackage().getImplementationVersion();
    if (impl != null && !impl.isBlank()) return impl;
    try {
      Path pom = Path.of("pom.xml");
      if (Files.exists(pom)) {
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(Files.readString(pom));
        if (m.find()) return m.group(1).trim();
      }
    } catch (IOException ignored) {
      // fall through
    }
    return "unknown";
  }

  /** Whether the skill files are actually on the classpath (they always are in a real build). */
  public static boolean skillResourceAvailable() {
    return Skill.class.getResource(RESOURCE_ROOT + "/SKILL.md") != null;
  }

  // ---- state (non-destructive; used by doctor) --------------------

  public enum Status { ABSENT, CURRENT, OUTDATED, UNMANAGED }

  public record SkillState(Status status, String version, String installed) {
    static SkillState absent() { return new SkillState(Status.ABSENT, null, null); }
    static SkillState unmanaged() { return new SkillState(Status.UNMANAGED, null, null); }
    static SkillState current(String v) { return new SkillState(Status.CURRENT, v, v); }
    static SkillState outdated(String pkg, String installed) {
      return new SkillState(Status.OUTDATED, pkg, installed);
    }
  }

  public static SkillState skillState(Path cwd, TargetSpec target, String packageVersion) {
    Path dir = cwd.resolve(target.projectDir());
    if (!Files.exists(dir.resolve("SKILL.md"))) {
      return SkillState.absent();
    }
    Path marker = dir.resolve(MARKER_FILENAME);
    if (!Files.exists(marker)) {
      return SkillState.unmanaged();
    }
    String installed;
    try {
      installed = readVersionMarker(Files.readString(marker));
    } catch (IOException e) {
      return SkillState.unmanaged();
    }
    if (installed == null) {
      return SkillState.unmanaged();
    }
    return installed.equals(packageVersion)
        ? SkillState.current(installed)
        : SkillState.outdated(packageVersion, installed);
  }

  // ---- install --------------------------------------------------

  public enum Action { CREATED, UPDATED, SKIPPED, BLOCKED }

  public record InstallResult(TargetSpec target, Action action, Path path, String detail) {}

  public record InstallOptions(Path cwd, String version, boolean user, boolean force, boolean dryRun) {
    public static InstallOptions of(Path cwd, String version) {
      return new InstallOptions(cwd, version, false, false, false);
    }
  }

  public static InstallResult installSkill(TargetSpec target, InstallOptions opts) {
    Path dest = opts.user() ? target.userDir() : opts.cwd().resolve(target.projectDir());
    Path markerFile = dest.resolve(MARKER_FILENAME);
    boolean existed = Files.exists(dest.resolve("SKILL.md"));

    if (existed) {
      boolean managed = Files.exists(markerFile);
      if (!managed && !opts.force()) {
        return new InstallResult(target, Action.BLOCKED, dest,
            "a skill directory exists here with no version marker (hand-customized?) — re-run with --force to overwrite");
      }
      if (managed && !opts.force()) {
        try {
          String installed = readVersionMarker(Files.readString(markerFile));
          if (opts.version().equals(installed)) {
            return new InstallResult(target, Action.SKIPPED, dest, "already up to date");
          }
        } catch (IOException ignored) {
          // treat as needing a refresh
        }
      }
      if (!opts.dryRun()) {
        deleteRecursively(dest);
      }
    }

    if (!opts.dryRun()) {
      try {
        Files.createDirectories(dest);
        int copied = writeSkillFiles(dest);
        if (copied == 0) {
          return new InstallResult(target, Action.BLOCKED, dest,
              "the skill files are not on the classpath — is this a real tamash-selenium build?");
        }
        Files.writeString(markerFile, versionMarker(opts.version()) + "\n");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return new InstallResult(target, existed ? Action.UPDATED : Action.CREATED, dest, null);
  }

  private static int writeSkillFiles(Path dest) throws IOException {
    int copied = 0;
    for (String rel : SKILL_FILES) {
      try (InputStream in = Skill.class.getResourceAsStream(RESOURCE_ROOT + "/" + rel)) {
        if (in == null) continue;
        Path target = dest.resolve(rel);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        copied++;
      }
    }
    return copied;
  }

  private static void deleteRecursively(Path dir) {
    if (!Files.exists(dir)) return;
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---- legacy artifacts ---------------------------------------

  /** Best-effort detection of the pre-{@code init-skill} install shape (the {@code adapters/}
   *  model and manual {@code dependency:unpack} leftovers), so {@code doctor}/{@code init-skill}
   *  can point at the stragglers to delete by hand. Returns the leftover paths that exist. */
  public static List<String> legacyInstallArtifacts(Path cwd) {
    List<String> found = new ArrayList<>();
    // an old dependency:unpack dropped the whole skills/ tree (adapters and all) somewhere
    for (String rel : List.of("target/tamash-skill/skills/tamash-selenium",
                              "skills/tamash-selenium/adapters")) {
      if (Files.exists(cwd.resolve(rel))) found.add(rel);
    }
    // an installed skill dir that still carries the legacy adapters/ subfolder
    for (TargetSpec t : TARGETS) {
      if (Files.exists(cwd.resolve(t.projectDir()).resolve("adapters"))) {
        found.add(t.projectDir().resolve("adapters").toString());
      }
    }
    Path mdc = cwd.resolve(Path.of(".cursor", "rules", "tamash-selenium.mdc"));
    if (Files.exists(mdc)) found.add(".cursor/rules/tamash-selenium.mdc");
    return found;
  }

  // ---- CLI: init-skill --------------------------------------

  private static final String HELP = """
      tamash-selenium init-skill — install the self-healing orchestration skill for your AI coding assistant

      Usage: tamash-selenium init-skill [options]

      Copies SKILL.md + references/ into (by default) both:
        .claude/skills/tamash-selenium/   — Claude Code
        .agents/skills/tamash-selenium/   — the cross-tool standard (Cursor, Copilot, Windsurf, Kiro, …)

      Options:
        --target claude|agents   install only one location (default: both)
        --user                   install under your home directory (covers every project)
        --force                  overwrite a hand-edited / unmarked copy
        --dry-run                print what would happen, change nothing
        --dir <path>             project root to install into (default: current directory)
        -h, --help               this message
      """;

  public static void run(String[] args) {
    String targetId = null;
    boolean user = false;
    boolean force = false;
    boolean dryRun = false;
    Path cwd = Path.of("").toAbsolutePath();

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--target" -> targetId = i + 1 < args.length ? args[++i] : null;
        case "--user" -> user = true;
        case "--force" -> force = true;
        case "--dry-run" -> dryRun = true;
        case "--dir" -> { if (i + 1 < args.length) cwd = Path.of(args[++i]).toAbsolutePath(); }
        case "-h", "--help" -> { System.out.println(HELP); return; }
        default -> { System.out.println("Unknown option: " + args[i]); System.out.println(HELP); System.exit(1); }
      }
    }

    List<TargetSpec> targets;
    if (targetId != null) {
      Optional<TargetSpec> t = getTarget(targetId);
      if (t.isEmpty()) {
        System.out.println("Unknown --target: " + targetId + " (expected 'claude' or 'agents')");
        System.exit(1);
        return;
      }
      targets = List.of(t.get());
    } else {
      targets = TARGETS;
    }

    String version = getPackageVersion();
    ConsoleStyle.section("init-skill");
    System.out.println("  tamash-selenium " + version + (dryRun ? "  (dry run)" : ""));

    InstallOptions opts = new InstallOptions(cwd, version, user, force, dryRun);
    boolean anyBlocked = false;
    for (TargetSpec t : targets) {
      InstallResult r = installSkill(t, opts);
      String where = r.path().toString();
      String line = switch (r.action()) {
        case CREATED -> ConsoleStyle.green("  [+] ") + "installed  " + where;
        case UPDATED -> ConsoleStyle.green("  [~] ") + "updated    " + where;
        case SKIPPED -> ConsoleStyle.dim("  [=] ") + "up to date " + where;
        case BLOCKED -> ConsoleStyle.yellow("  [!] ") + where + " — " + r.detail();
      };
      System.out.println(line);
      if (r.action() == Action.BLOCKED) anyBlocked = true;
    }

    List<String> legacy = legacyInstallArtifacts(cwd);
    if (!legacy.isEmpty()) {
      System.out.println();
      System.out.println(ConsoleStyle.dim("  Leftovers from an older setup — safe to delete by hand:"));
      for (String p : legacy) System.out.println(ConsoleStyle.dim("    " + p));
    }

    if (anyBlocked) System.exit(1);
  }
}
