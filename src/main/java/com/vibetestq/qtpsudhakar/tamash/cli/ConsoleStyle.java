package com.vibetestq.qtpsudhakar.tamash.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Port of src/cli/console-style.ts — plain ANSI styling + a box-drawing table, no dependency.
 *  Colour is disabled outside a real terminal (piped output, {@code NO_COLOR}, {@code TERM=dumb}). */
public final class ConsoleStyle {
  private ConsoleStyle() {}

  private static final char ESC = 27;
  public static final boolean USE_COLOR =
      System.console() != null && System.getenv("NO_COLOR") == null && !"dumb".equals(System.getenv("TERM"));

  private static String paint(String code, String text) {
    return USE_COLOR ? ESC + "[" + code + "m" + text + ESC + "[0m" : text;
  }

  public static String bold(String s) { return paint("1", s); }
  public static String dim(String s) { return paint("2", s); }
  public static String green(String s) { return paint("32", s); }
  public static String yellow(String s) { return paint("33", s); }
  public static String red(String s) { return paint("31", s); }
  public static String cyan(String s) { return paint("36", s); }

  public static void section(String title) {
    System.out.println("\n" + bold(title));
  }

  private static final Pattern ANSI = Pattern.compile(ESC + "\\[[0-9;]*m");

  public static int visibleLength(String s) {
    return ANSI.matcher(s).replaceAll("").length();
  }

  private static String padCell(String s, int width) {
    int pad = Math.max(0, width - visibleLength(s));
    return s + " ".repeat(pad);
  }

  public static String truncateEnd(String s, int max) {
    return s.length() > max ? s.substring(0, max - 1) + "…" : s;
  }

  public static String truncateStart(String s, int max) {
    return s.length() > max ? "…" + s.substring(s.length() - max + 1) : s;
  }

  public static void renderTable(List<String> headers, List<List<String>> rows, String indent) {
    int[] widths = new int[headers.size()];
    for (int i = 0; i < headers.size(); i++) {
      widths[i] = visibleLength(headers.get(i));
      for (List<String> r : rows) {
        widths[i] = Math.max(widths[i], visibleLength(i < r.size() ? r.get(i) : ""));
      }
    }
    System.out.println(rule(indent, widths, "┌", "┬", "┐"));
    System.out.println(rowLine(indent, widths, headers.stream().map(ConsoleStyle::bold).toList()));
    System.out.println(rule(indent, widths, "├", "┼", "┤"));
    for (List<String> row : rows) {
      System.out.println(rowLine(indent, widths, row));
    }
    System.out.println(rule(indent, widths, "└", "┴", "┘"));
  }

  public static void renderTable(List<String> headers, List<List<String>> rows) {
    renderTable(headers, rows, "  ");
  }

  private static String rule(String indent, int[] widths, String l, String m, String r) {
    StringBuilder sb = new StringBuilder(indent).append(l);
    for (int i = 0; i < widths.length; i++) {
      sb.append("─".repeat(widths[i] + 2));
      sb.append(i < widths.length - 1 ? m : r);
    }
    return sb.toString();
  }

  private static String rowLine(String indent, int[] widths, List<String> cells) {
    StringBuilder sb = new StringBuilder(indent).append("│ ");
    for (int i = 0; i < widths.length; i++) {
      sb.append(padCell(i < cells.size() ? cells.get(i) : "", widths[i]));
      sb.append(i < widths.length - 1 ? " │ " : " │");
    }
    return sb.toString();
  }

  /** A real human at a real terminal — never in CI, never with piped stdin/stdout. */
  public static boolean isInteractive() {
    return System.console() != null && System.getenv("CI") == null;
  }

  /** Reads one line; only {@code y}/{@code yes} (any case) yields true. */
  public static boolean confirm(String question) {
    System.out.print(question);
    System.out.flush();
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String answer = br.readLine();
      return answer != null && answer.trim().matches("(?i)y(es)?");
    } catch (Exception e) {
      return false;
    }
  }
}
