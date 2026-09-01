package com.vibetestq.qtpsudhakar.tamash.cli;

import com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** A plain regex scan over source text, not a real Java parser — good enough to flag likely
 *  issues for a human (or an AI IDE) to review, not a strict/exhaustive linter. */
final class LocatorScanner {
  private LocatorScanner() {}

  // By.xpath / By.cssSelector carry no semantic meaning on their own, so a non-descriptive
  // variable name hurts them the most; By.id / By.name are already self-describing.
  private static final Pattern BY_FACTORY = Pattern.compile(
      "\\bBy\\.(id|name|cssSelector|xpath|className|tagName|linkText|partialLinkText)\\s*\\(");
  private static final Set<String> HIGH_PRIORITY = Set.of("cssSelector", "xpath", "className", "partialLinkText");

  // `By x = By.xpath(...)`, `private final By x = By.xpath(...)`, `this.x = By.xpath(...)`
  private static final Pattern ASSIGNMENT = Pattern.compile("(?:this\\.)?(\\w+)\\s*=\\s*$");

  record Occurrence(String file, int line, String method, String snippet, boolean described, String priority) {}

  static List<Occurrence> scanDirectory(Path dir) {
    List<Occurrence> occurrences = new ArrayList<>();
    if (!Files.isDirectory(dir)) {
      return occurrences;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        occurrences.addAll(scanFile(file.toString(), Files.readString(file)));
      }
    } catch (IOException e) {
      System.out.println("[self-healer] Could not scan " + dir + ": " + e.getMessage());
    }
    return occurrences;
  }

  static List<Occurrence> scanFile(String filePath, String content) {
    List<Occurrence> occurrences = new ArrayList<>();
    String[] lines = content.split("\n", -1);

    Matcher matcher = BY_FACTORY.matcher(content);
    while (matcher.find()) {
      String method = matcher.group(1);
      int lineNo = lineNumberAt(content, matcher.start());
      String lineText = lineNo - 1 < lines.length ? lines[lineNo - 1].strip() : "";
      if (lineText.startsWith("//") || lineText.startsWith("*")) {
        continue;
      }

      String beforeCall = content.substring(Math.max(0, matcher.start() - 80), matcher.start());
      Matcher am = ASSIGNMENT.matcher(beforeCall);
      String varName = am.find() ? am.group(1) : null;
      boolean described = varName != null && SourceLocations.decodeVariableName(varName) != null;

      String priority = HIGH_PRIORITY.contains(method) && !described ? "high" : "normal";
      String snippet = lineText.length() > 100 ? lineText.substring(0, 100) : lineText;
      occurrences.add(new Occurrence(filePath, lineNo, method, snippet, described, priority));
    }
    return occurrences;
  }

  static boolean isTestFile(String path) {
    String name = path.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    return name.endsWith("Test.java") || name.endsWith("Tests.java") || name.endsWith("IT.java")
        || name.endsWith("Steps.java");
  }

  private static int lineNumberAt(String content, int index) {
    int line = 1;
    for (int i = 0; i < index; i++) {
      if (content.charAt(i) == '\n') line++;
    }
    return line;
  }
}
