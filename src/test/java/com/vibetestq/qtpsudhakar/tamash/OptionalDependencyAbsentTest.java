package com.vibetestq.qtpsudhakar.tamash;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code junit-jupiter-api} is {@code <optional>}, {@code testng} and {@code cucumber-java} are
 * {@code <provided>} — a consumer who uses only one test framework must not need the others on
 * the classpath. This loads the core self-healing classes through a classloader with JUnit,
 * TestNG and Cucumber stripped out and forces their static initialisers; a hard reference to any
 * of those frameworks from the core path would surface here as {@code NoClassDefFoundError}.
 *
 * <p>The Java analog of pw's CJS/ESM-consumer regression test.
 */
class OptionalDependencyAbsentTest {

  private static final String JUNIT = "junit|apiguardian|opentest4j";
  private static final String TESTNG = "testng|jcommander";
  private static final String CUCUMBER = "cucumber|gherkin|datatable|docstring|tag-expressions"
      + "|html-formatter|xml-formatter|ci-environment|\\bmessages-\\d|\\bquery-\\d|jquery";

  private static URLClassLoader isolatedLoader(String stripRegex) throws Exception {
    Pattern strip = Pattern.compile("(?i)(" + stripRegex + ")");
    String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
    List<URL> urls = new ArrayList<>();
    for (String e : entries) {
      String name = new File(e).getName();
      if (name.endsWith(".jar") && strip.matcher(name).find()) continue;
      urls.add(new File(e).toURI().toURL());
    }
    // parent = platform loader: JDK classes resolve, nothing from the app classpath leaks in
    return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
  }

  private static void assertLinks(URLClassLoader cl, List<String> fqcns) {
    for (String fqcn : fqcns) {
      assertDoesNotThrow(() -> Class.forName(fqcn, true, cl), fqcn);
    }
  }

  private static void assertGone(URLClassLoader cl, List<String> fqcns) {
    for (String fqcn : fqcns) {
      assertThrows(ClassNotFoundException.class, () -> Class.forName(fqcn, false, cl),
          fqcn + " should not be reachable");
    }
  }

  private static Object defaultReturn(Method m) {
    Class<?> r = m.getReturnType();
    if (r == boolean.class) return false;
    if (r == int.class) return 0;
    if (r == long.class) return 0L;
    if (r == void.class) return null;
    return null;
  }

  private static final List<String> CORE = List.of(
      "com.vibetestq.qtpsudhakar.tamash.SelfHealingDriver",
      "com.vibetestq.qtpsudhakar.tamash.bindings.Bindings",
      "com.vibetestq.qtpsudhakar.tamash.bindings.HealingInvocationHandler",
      "com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations",
      "com.vibetestq.qtpsudhakar.tamash.bindings.Steps",
      "com.vibetestq.qtpsudhakar.tamash.healer.Healer",
      "com.vibetestq.qtpsudhakar.tamash.healer.DomSnapshot",
      "com.vibetestq.qtpsudhakar.tamash.healer.DurableLocator",
      "com.vibetestq.qtpsudhakar.tamash.healer.HealCache",
      "com.vibetestq.qtpsudhakar.tamash.healer.HealLog",
      "com.vibetestq.qtpsudhakar.tamash.healer.ActionRecovery",
      "com.vibetestq.qtpsudhakar.tamash.healer.providers.ProviderFactory",
      "com.vibetestq.qtpsudhakar.tamash.report.TamashReport",
      "com.vibetestq.qtpsudhakar.tamash.pagefactory.TamashPageFactory",
      "com.vibetestq.qtpsudhakar.tamash.cli.Main",
      "com.vibetestq.qtpsudhakar.tamash.cli.Doctor",
      "com.vibetestq.qtpsudhakar.tamash.cli.ApplyHeals",
      "com.vibetestq.qtpsudhakar.tamash.cli.Skill");

  @Test
  void coreClassesLinkAndWrapWorks_withNoTestFrameworkAtAll() throws Exception {
    try (URLClassLoader cl = isolatedLoader(JUNIT + "|" + TESTNG + "|" + CUCUMBER)) {
      assertGone(cl, List.of("org.junit.jupiter.api.Test",
                             "org.testng.annotations.Test",
                             "io.cucumber.java.en.Given"));
      assertNotNull(Class.forName("org.openqa.selenium.WebDriver", false, cl));
      assertLinks(cl, CORE);

      // actually run SelfHealingDriver.wrap through the isolated loader
      Class<?> wd = Class.forName("org.openqa.selenium.WebDriver", true, cl);
      Class<?> js = Class.forName("org.openqa.selenium.JavascriptExecutor", true, cl);
      Object fakeDriver = Proxy.newProxyInstance(cl, new Class<?>[]{wd, js},
          (proxy, method, args) -> defaultReturn(method));
      Class<?> shd = Class.forName("com.vibetestq.qtpsudhakar.tamash.SelfHealingDriver", true, cl);
      Object wrapped = shd.getMethod("wrap", wd).invoke(null, fakeDriver);
      assertNotNull(wrapped);
      assertTrue(wd.isInstance(wrapped), "wrap() must return a WebDriver");
    }
  }

  @Test
  void junitIntegrationLinks_withoutTestNgOrCucumber() throws Exception {
    try (URLClassLoader cl = isolatedLoader(TESTNG + "|" + CUCUMBER)) {
      assertGone(cl, List.of("org.testng.annotations.Test", "io.cucumber.java.en.Given"));
      assertLinks(cl, List.of(
          "com.vibetestq.qtpsudhakar.tamash.junit.TamashSeleniumExtension",
          "com.vibetestq.qtpsudhakar.tamash.junit.UseTamashSelenium",
          "com.vibetestq.qtpsudhakar.tamash.report.TamashReportListener"));
    }
  }

  @Test
  void testNgIntegrationLinks_withoutJUnitOrCucumber() throws Exception {
    try (URLClassLoader cl = isolatedLoader(JUNIT + "|" + CUCUMBER)) {
      assertGone(cl, List.of("org.junit.jupiter.api.Test", "io.cucumber.java.en.Given"));
      assertLinks(cl, List.of(
          "com.vibetestq.qtpsudhakar.tamash.testng.TamashSeleniumTestNgTest",
          "com.vibetestq.qtpsudhakar.tamash.testng.TamashSeleniumTestNgListener"));
    }
  }

  @Test
  void cucumberIntegrationLinks_withoutJUnitOrTestNg() throws Exception {
    try (URLClassLoader cl = isolatedLoader(JUNIT + "|" + TESTNG)) {
      assertGone(cl, List.of("org.junit.jupiter.api.Test", "org.testng.annotations.Test"));
      assertLinks(cl, List.of(
          "com.vibetestq.qtpsudhakar.tamash.cucumber.TamashSeleniumCucumberHooks",
          "com.vibetestq.qtpsudhakar.tamash.cucumber.TamashSeleniumScenario"));
    }
  }
}
