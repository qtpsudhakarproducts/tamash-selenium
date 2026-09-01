package com.vibetestq.qtpsudhakar.tamash.cli;

import com.vibetestq.qtpsudhakar.tamash.Env;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.HealProvider;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderResult;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.SuggestSelectorInput;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.vibetestq.qtpsudhakar.tamash.cli.ConsoleStyle.*;

/** Port of src/cli/doctor.ts (Selenium-adapted). Confirms provider connectivity, reports the
 *  implicit-wait setting, flags brittle locators bound to non-descriptive variable names, and
 *  flags locators written straight into test files instead of Page Objects. */
public final class Doctor {
  private Doctor() {}

  private static final double CONNECTIVITY_TIMEOUT_MS = 15000.0;
  private static final String CONNECTIVITY_SNAPSHOT =
      "- generic \"tamash-selenium doctor check\" [ref=e1]:\n  - button \"OK\" [ref=e2]";

  private record Check(String check, String status, String detail) {}
  private static final List<Check> SUMMARY = new ArrayList<>();

  private static void record(String check, String status, String detail) {
    SUMMARY.add(new Check(check, status, detail));
  }

  private static String colorStatus(String status) {
    return switch (status) {
      case "OK" -> green(status);
      case "WARN" -> yellow(status);
      case "FAIL" -> red(status);
      case "INFO" -> cyan(status);
      default -> dim(status);
    };
  }

  public static void main(String[] args) {
    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    String command = args.length > 0 ? args[0] : null;
    if (!"doctor".equals(command)) {
      System.out.println("Usage: mvn exec:java -Dexec.args=\"doctor [--dir <path>]\"");
      if (command != null) {
        System.exit(1);
      }
      return;
    }
    String dir = "src/test/java";
    for (int i = 1; i < args.length - 1; i++) {
      if ("--dir".equals(args[i])) {
        dir = args[i + 1];
      }
    }
    runDoctor(dir);
  }

  public static void runDoctor(String testDir) {
    System.out.println(bold("tamash-selenium doctor"));
    System.out.println(dim("─".repeat(25)));

    checkProviderConnectivity();

    checkImplicitWait();

    section("Locators");
    Path resolvedDir = Path.of(testDir).toAbsolutePath().normalize();
    System.out.println("  Scanning " + resolvedDir + " for locators...");
    System.out.println(dim("  (static text scan, not a real parser — review these before acting on them)\n"));
    List<LocatorScanner.Occurrence> occurrences = LocatorScanner.scanDirectory(resolvedDir);
    checkLocatorNaming(occurrences);
    checkPageObjectUsage(occurrences);

    checkSkill();

    printSummary();
  }

  private static void checkSkill() {
    section("Skill");
    Path cwd = Path.of("").toAbsolutePath();
    String pkgVersion = Skill.getPackageVersion();
    boolean anyPresent = false;
    boolean anyStale = false;

    for (Skill.TargetSpec t : Skill.TARGETS) {
      Skill.SkillState st = Skill.skillState(cwd, t, pkgVersion);
      switch (st.status()) {
        case ABSENT -> System.out.println("  " + dim(t.projectDir() + " — not installed"));
        case CURRENT -> {
          anyPresent = true;
          System.out.println("  " + t.projectDir() + " — " + st.version() + " (current)");
        }
        case OUTDATED -> {
          anyPresent = true;
          anyStale = true;
          System.out.println("  " + t.projectDir() + " — " + st.installed()
              + ", package is " + st.version());
        }
        case UNMANAGED -> {
          anyPresent = true;
          anyStale = true;
          System.out.println("  " + t.projectDir() + " — present, no version marker");
        }
      }
    }

    List<String> legacy = Skill.legacyInstallArtifacts(cwd);
    for (String p : legacy) {
      System.out.println("  " + dim("leftover from an older setup: " + p));
    }

    if (!anyPresent) {
      System.out.println(dim("          run: mvn -q exec:java -Dexec.args=\"init-skill\""));
      record("Skill", "INFO", "Skill not installed (.claude/skills, .agents/skills)");
    } else if (anyStale) {
      System.out.println(dim("          run: mvn -q exec:java -Dexec.args=\"init-skill\" to refresh"));
      record("Skill", "WARN", "Installed skill is behind the package or unmanaged — run init-skill");
    } else if (!legacy.isEmpty()) {
      record("Skill", "INFO", "Skill installed and current; " + legacy.size() + " legacy leftover(s) to delete");
    } else {
      record("Skill", "OK", "Skill installed and current");
    }
  }

  private static void checkProviderConnectivity() {
    section("AI Provider");
    String enabledValue = Env.get("HEALER_ENABLED");
    System.out.println("  HEALER_ENABLED: " + (enabledValue != null ? enabledValue : dim("(unset — defaults to true)")));
    if (!Healer.isHealingEnabled()) {
      System.out.println("  Healing is currently OFF. Set HEALER_ENABLED=true (or remove the line) to turn it back on.");
      record("AI Provider", "OFF", "Healing disabled (HEALER_ENABLED=false)");
      return;
    }

    String providerName = Env.get("HEALER_PROVIDER");
    if (providerName == null || providerName.isBlank()) {
      System.out.println("  " + cyan("[INFO]") + " HEALER_PROVIDER is not set — defaulting to the rule-based " + bold("tamash") + " provider");
      System.out.println("         (no key, no network, no tokens; never guesses). Set HEALER_PROVIDER");
      System.out.println("         (ollama | openai | anthropic | gemini | claude-subscription | copilot-subscription)");
      System.out.println("         + its API key/model for AI-backed healing.");
      providerName = "tamash";
    }

    ProviderFactory.resetCache();
    HealProvider provider = ProviderFactory.getHealProvider();
    if (provider == null) {
      System.out.println("  " + red("[FAIL]") + " HEALER_PROVIDER=" + providerName + ", but its required model/API key env vars are missing.");
      record("AI Provider", "FAIL", "HEALER_PROVIDER=" + providerName + ", missing required env vars");
      return;
    }

    System.out.println("  Testing connectivity to " + bold(provider.getName()) + "...");
    ProviderResult result = provider.suggestSelector(new SuggestSelectorInput(
        "click", "tamash-selenium doctor connectivity check", CONNECTIVITY_SNAPSHOT, CONNECTIVITY_TIMEOUT_MS));
    if (result != null) {
      System.out.println("  " + green("[OK]") + " Connected to " + provider.getName() + " successfully.");
      record("AI Provider", "OK", "Connected to " + provider.getName());
    } else {
      System.out.println("  " + red("[FAIL]") + " Could not get a valid response from " + provider.getName() + ".");
      System.out.println("         Check the warning above (if any), your API key, model name, and network connection.");
      record("AI Provider", "FAIL", "No valid response from " + provider.getName());
    }
  }

  private static void checkImplicitWait() {
    section("Implicit Wait");
    System.out.println("  " + green("[OK]") + " The tamash-selenium lifecycle pins Selenium's implicit wait to 0ms so a "
        + "broken findElement surfaces immediately for self-healing.");
    System.out.println(dim("        Use explicit WebDriverWait for real synchronisation — but note a locator broken "
        + "inside wait.until(...) is not healed (see the README). A direct driver.findElement(broken) IS."));
    record("Implicit Wait", "OK", "Implicit wait pinned to 0 by the lifecycle");
  }

  private static void checkLocatorNaming(List<LocatorScanner.Occurrence> occurrences) {
    List<LocatorScanner.Occurrence> weak = occurrences.stream()
        .filter(o -> "high".equals(o.priority())).toList();
    if (weak.isEmpty()) {
      System.out.println("  " + green("[OK]") + " Every brittle locator is bound to a descriptive variable name.");
      record("Locator naming", "OK", "No brittle locators with a non-descriptive name");
      return;
    }
    System.out.println("  " + yellow("[WARN]") + " Found " + bold(String.valueOf(weak.size()))
        + " brittle CSS/XPath locator(s) with no descriptive variable name:\n");
    List<List<String>> rows = new ArrayList<>();
    for (LocatorScanner.Occurrence f : weak) {
      String rel = Path.of("").toAbsolutePath().relativize(Path.of(f.file())).toString().replace('\\', '/');
      rows.add(List.of(dim(truncateStart(rel + ":" + f.line(), 42)), truncateEnd(f.snippet(), 70)));
    }
    renderTable(List.of("Location", "Snippet"), rows, "    ");
    record("Locator naming", "WARN", weak.size() + " brittle locator(s) with a non-descriptive name");
    System.out.println();
    System.out.println("  Recommendation: bind these to a descriptive field so the healer knows what they are. Example:");
    System.out.println(dim("    private final By usernameTextbox = By.cssSelector(\"input[name='username']\");"));
  }

  private static void checkPageObjectUsage(List<LocatorScanner.Occurrence> occurrences) {
    Set<String> testFiles = new LinkedHashSet<>();
    long inline = 0;
    for (LocatorScanner.Occurrence o : occurrences) {
      if (LocatorScanner.isTestFile(o.file())) {
        testFiles.add(o.file());
        inline++;
      }
    }
    if (inline == 0) {
      record("Page Objects", "OK", "No locators defined directly inside test files");
      return;
    }
    System.out.println();
    System.out.println("  " + cyan("[INFO]") + " Found " + inline + " locator(s) defined directly inside " + testFiles.size() + " test file(s).");
    System.out.println("        Best practice: move locators into Page Object classes rather than writing them straight into tests.");
    record("Page Objects", "INFO", inline + " locator(s) inline across " + testFiles.size() + " file(s)");
  }

  private static void printSummary() {
    section("Summary");
    List<List<String>> rows = new ArrayList<>();
    for (Check c : SUMMARY) {
      rows.add(List.of(c.check(), colorStatus(c.status()), truncateEnd(c.detail(), 78)));
    }
    renderTable(List.of("Check", "Status", "Detail"), rows);
  }
}
