package com.vibetestq.qtpsudhakar.tamash.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** CLI entry point: {@code doctor} | {@code apply-heals}. Set as {@code exec.mainClass} in pom.xml.
 *  Run via {@code mvn exec:java -Dexec.args="doctor"} / {@code -Dexec.args="apply-heals --dry-run"}. */
public final class Main {
  private Main() {}

  private static final String USAGE =
      "Usage: tamash-selenium doctor [--dir <path>]\n"
      + "     | apply-heals [--dry-run] [--logs-dir <path>] [--yes]\n"
      + "     | init-skill [--target claude|agents] [--user] [--force] [--dry-run] [--dir <path>]";

  public static void main(String[] args) {
    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    String command = args.length > 0 ? args[0] : null;
    String[] rest = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

    try {
      switch (command == null ? "" : command) {
        case "doctor" -> {
          String dir = "src/test/java";
          for (int i = 0; i < rest.length - 1; i++) {
            if ("--dir".equals(rest[i])) {
              dir = rest[i + 1];
            }
          }
          Doctor.runDoctor(dir);
        }
        case "apply-heals" -> ApplyHeals.run(rest);
        case "init-skill" -> Skill.run(rest);
        case "", "--help", "-h" -> System.out.println(USAGE);
        default -> {
          System.out.println("Unknown command: " + command);
          System.out.println(USAGE);
          System.exit(1);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
