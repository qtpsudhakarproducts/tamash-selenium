package com.vibetestq.qtpsudhakar.tamash.cli;

import com.vibetestq.qtpsudhakar.tamash.healer.HealLog;
import com.vibetestq.qtpsudhakar.tamash.healer.providers.AiSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ApplyHealsTest {

  @Test
  void findFactoryCallOnLine_balancesParensAndStrings() {
    String src = "  private final By user = By.cssSelector(\"input[name='x)y']\"); // login\n";
    var range = ApplyHeals.findFactoryCallOnLine(src, 1);
    assertNotNull(range);
    assertEquals("By.cssSelector(\"input[name='x)y']\")", src.substring(range.dotIndex(), range.callEnd()));
  }

  @Test
  void planFixes_rewritesJavaSource(@TempDir Path cwd) throws Exception {
    Path pkg = cwd.resolve("src/test/java/com/foo");
    Files.createDirectories(pkg);
    Path file = pkg.resolve("LoginPage.java");
    Files.writeString(file,
        "package com.foo;\n"
        + "class LoginPage {\n"
        + "  private final By username = By.xpath(\"//old\");\n"
        + "}\n");

    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = "2026-08-28T10:00:00Z";
    e.file = "src/test/java/com/foo/LoginPage.java";
    e.line = 3;
    e.action = "sendKeys";
    e.suggestion = AiSuggestion.id("username");
    e.testSelector = "com.foo.LoginTest#logsIn";

    ApplyHeals.Plan plan = ApplyHeals.planFixes(cwd, java.util.List.of(e));
    assertEquals(1, plan.outcomes().size());
    ApplyHeals.FixOutcome o = plan.outcomes().get(0);
    assertTrue(o.applied(), o.reason());
    assertEquals("By.xpath(\"//old\")", o.before());
    assertEquals("By.id(\"username\")", o.after());
    assertEquals(java.util.List.of("com.foo.LoginTest#logsIn"), plan.affectedTests());

    String rewritten = plan.fileContents().values().iterator().next();
    assertTrue(rewritten.contains("private final By username = By.id(\"username\");"));
  }

  @Test
  void planFixes_rewritesFindByAnnotation(@TempDir Path cwd) throws Exception {
    Path pkg = cwd.resolve("src/test/java/com/foo");
    Files.createDirectories(pkg);
    Path file = pkg.resolve("LoginPage.java");
    Files.writeString(file,
        "package com.foo;\n"
        + "class LoginPage {\n"
        + "  @FindBy(css = \"#old\")\n"
        + "  private WebElement usernameTextbox;\n"
        + "}\n");

    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = "2026-08-28T10:00:00Z";
    e.file = "src/test/java/com/foo/LoginPage.java";
    e.line = 3; // the @FindBy line
    e.action = "sendKeys";
    e.suggestion = AiSuggestion.id("username");

    ApplyHeals.Plan plan = ApplyHeals.planFixes(cwd, java.util.List.of(e));
    ApplyHeals.FixOutcome o = plan.outcomes().get(0);
    assertTrue(o.applied(), o.reason());
    assertEquals("@FindBy(css = \"#old\")", o.before());
    assertEquals("@FindBy(id = \"username\")", o.after());
    assertTrue(plan.fileContents().values().iterator().next().contains("@FindBy(id = \"username\")"));
  }

  @Test
  void planFixes_rewritesByFieldDeclarationWhenCallSiteHasNoLiteral(@TempDir Path cwd) throws Exception {
    Path pkg = cwd.resolve("src/test/java/com/foo");
    Files.createDirectories(pkg);
    Path file = pkg.resolve("AddEmployeePage.java");
    Files.writeString(file,
        "package com.foo;\n"
        + "class AddEmployeePage {\n"
        + "  private final By firstName = By.name(\"first_name\");\n"       // line 3 — declaration
        + "  WebElement first() { return getElement(firstName); }\n"        // line 4 — call site
        + "}\n");

    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = "2026-08-28T10:00:00Z";
    e.file = "src/test/java/com/foo/AddEmployeePage.java";
    e.line = 4;                                                            // recorded at the call
    e.declarationLocation = "src/test/java/com/foo/AddEmployeePage.java:3"; // resolved to the field
    e.action = "sendKeys";
    e.suggestion = AiSuggestion.nameAttr("firstName");

    ApplyHeals.Plan plan = ApplyHeals.planFixes(cwd, java.util.List.of(e));
    ApplyHeals.FixOutcome o = plan.outcomes().get(0);
    assertTrue(o.applied(), o.reason());
    assertEquals(3, o.line());
    assertEquals("By.name(\"first_name\")", o.before());
    assertEquals("By.name(\"firstName\")", o.after());
    assertTrue(plan.fileContents().values().iterator().next()
        .contains("private final By firstName = By.name(\"firstName\");"));
  }

  @Test
  void planFixes_skipsAuditOnlyEntry(@TempDir Path cwd) throws Exception {
    Path pkg = cwd.resolve("src/test/java/com/foo");
    Files.createDirectories(pkg);
    Files.writeString(pkg.resolve("P.java"), "class P { By x = By.cssSelector(\"#a\"); }\n");
    HealLog.Entry e = new HealLog.Entry();
    e.timestamp = "t";
    e.file = "src/test/java/com/foo/P.java";
    e.line = 1;
    e.action = "click";
    e.suggestion = null; // audit-only
    e.reviewNote = "one-shot ref heal";
    ApplyHeals.Plan plan = ApplyHeals.planFixes(cwd, java.util.List.of(e));
    assertFalse(plan.outcomes().get(0).applied());
    assertTrue(plan.outcomes().get(0).reason().contains("one-shot"));
  }
}
