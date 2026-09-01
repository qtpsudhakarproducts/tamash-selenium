package com.vibetestq.qtpsudhakar.tamash.bindings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceLocationsTest {

  @Test
  void findAssignmentEquals_ignoresEqualsInsideStrings() {
    // The CSS-attribute-selector regression: `=` inside a string literal is not an assignment.
    String line = "    Locator u = page.locator(\"input[name='wrongname']\").describe(\"x\");";
    int eq = SourceLocations.findAssignmentEquals(line);
    assertTrue(eq > 0);
    // whatever sits right before the first real `=` is the variable
    assertTrue(line.substring(0, eq).trim().endsWith("u"));
  }

  @Test
  void findAssignmentEquals_skipsComparisons() {
    assertEquals(-1, SourceLocations.findAssignmentEquals("if (a == b) {"));
    assertEquals(-1, SourceLocations.findAssignmentEquals("return x >= y;"));
  }

  @Test
  void decodeVariableName_prefixStyle() {
    var d = SourceLocations.decodeVariableName("txtEmployeeId");
    assertNotNull(d);
    assertEquals("Employee Id", d.name());
    assertEquals("textbox", d.typeHint());
  }

  @Test
  void decodeVariableName_suffixStyle() {
    var d = SourceLocations.decodeVariableName("submitButton");
    assertNotNull(d);
    assertEquals("Submit", d.name());
    assertEquals("button", d.typeHint());
  }

  @Test
  void decodeVariableName_acronymBoundary() {
    var d = SourceLocations.decodeVariableName("employeeIDNumber");
    assertNotNull(d);
    assertEquals("Employee ID Number", d.name());
  }

  @Test
  void decodeVariableName_prefixFalsePositiveGuard() {
    // "imgur" must NOT be read as "img" + "ur" (no uppercase/separator boundary after the prefix).
    var d = SourceLocations.decodeVariableName("imgur");
    assertNotNull(d);
    assertNull(d.typeHint());
    assertEquals("Imgur", d.name());
  }

  @Test
  void decodeVariableName_meaninglessReturnsNull() {
    assertNull(SourceLocations.decodeVariableName("el1"));
    assertNull(SourceLocations.decodeVariableName("locator"));
    assertNull(SourceLocations.decodeVariableName("btn")); // bare affix
  }

  // ---- assertion / negative-find context (line-based) --------------------

  @Test
  void isAssertionLine_matchesAssertionShapes() {
    assertTrue(SourceLocations.isAssertionLine("assertEquals(\"Welcome\", driver.findElement(header).getText());"));
    assertTrue(SourceLocations.isAssertionLine("    Assert.assertTrue(loginPage.banner.isDisplayed());"));
    assertTrue(SourceLocations.isAssertionLine("assertThat(driver.findElement(x).getText()).isEqualTo(\"y\");"));
    assertTrue(SourceLocations.isAssertionLine("verifyElementText(errorMsg, \"required\");"));
    assertFalse(SourceLocations.isAssertionLine("driver.findElement(loginButton).click();"));
    assertFalse(SourceLocations.isAssertionLine("WebElement e = driver.findElement(by);"));
  }

  @org.junit.jupiter.api.Test
  void extractArgIdentifier_findsLocatorArgToAUtil() {
    assertEquals("loginButton", argId("WebUtil.click(driver, loginButton);"));
    assertEquals("usernameField", argId("this.helper.type(usernameField, \"admin\");"));
    assertEquals("Txt_FirstName", argId("return getElement(Txt_FirstName);"));       // bare call
    assertEquals("txtUser", argId("wait.until(ExpectedConditions.presenceOfElementLocated(txtUser));"));
    assertNull(argId("service.process(reportName, config);"));
    assertNull(argId("doStuff();"));
    assertNull(argId("getElement(loc);"));                                           // "loc" is noise
  }

  @org.junit.jupiter.api.Test
  void locatorishToken_ignoresAccessorMethodNames() {
    // getElement / findElement etc. end in "element" but are plumbing, not locators
    assertNull(memberId("return getElement(loc);"));
    assertNull(memberId("driver.findElement(by).click();"));
  }

  @org.junit.jupiter.api.Test
  void extractLocatorishToken_enumAndFluentChains() {
    assertEquals("txtUserName", memberId("LoginPage.txtUserName.enterText(\"admin\");"));
    assertEquals("usernameField", memberId("driver.wait(); loginPage.usernameField().sendKeys(\"x\");"));
    assertNull(memberId("loginPage.doLogin(\"admin\", \"pw\");"));   // "doLogin" isn't locator-ish
    assertNull(memberId("service.process(reportName);"));
  }

  /** Both helpers read a file line by "path:line" — feed a temp file. */
  private static String argId(String line) { return read(line, true); }
  private static String memberId(String line) { return read(line, false); }

  private static String read(String line, boolean arg) {
    try {
      java.nio.file.Path p = java.nio.file.Files.createTempFile("srcloc", ".java");
      java.nio.file.Files.writeString(p, line + "\n");
      String loc = p.toString().replace('\\', '/') + ":1";
      return arg ? SourceLocations.extractArgIdentifier(loc) : SourceLocations.extractLocatorishToken(loc);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void isNegativeLine_matchesAbsenceExpectations() {
    assertTrue(SourceLocations.isNegativeLine("wait.until(ExpectedConditions.invisibilityOfElementLocated(spinner));"));
    assertTrue(SourceLocations.isNegativeLine("assertThrows(NoSuchElementException.class, () -> driver.findElement(gone));"));
    assertTrue(SourceLocations.isNegativeLine("wait.until(ExpectedConditions.stalenessOf(row));"));
    assertTrue(SourceLocations.isNegativeLine("assertTrue(isElementAbsent(deleteBtn));"));
    assertFalse(SourceLocations.isNegativeLine("assertEquals(\"Welcome\", driver.findElement(header).getText());"));
    assertFalse(SourceLocations.isNegativeLine("driver.findElement(loginButton).click();"));
  }
}
