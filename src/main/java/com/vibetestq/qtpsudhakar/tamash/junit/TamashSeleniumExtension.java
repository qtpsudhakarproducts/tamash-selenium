package com.vibetestq.qtpsudhakar.tamash.junit;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.SeleniumLifecycle;
import com.vibetestq.qtpsudhakar.tamash.TamashHeals;
import com.vibetestq.qtpsudhakar.tamash.healer.SelfHealingReport;
import com.vibetestq.qtpsudhakar.tamash.report.TamashReport;
import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A self-contained Selenium + JUnit 5 lifecycle: a WebDriver launched once per test class (with
 * {@code TAMASH_REUSE_DRIVER=true}) or fresh per test method, healing-wrapped and injected as a
 * method parameter. Heals are attached to the native test result the same way the Playwright port
 * publishes them.
 */
public class TamashSeleniumExtension implements
    BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver, TestWatcher {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(TamashSeleniumExtension.class);

  private record ClassResources(SeleniumLifecycle.Session session) {}
  private record MethodResources(SeleniumLifecycle.Scope scope) {}

  @Override
  public void beforeAll(ExtensionContext context) {
    TamashReport.enableIfConfigured();
    context.getStore(NAMESPACE).put("resources", new ClassResources(SeleniumLifecycle.launch()));
  }

  @Override
  public void afterAll(ExtensionContext context) {
    ClassResources r = context.getStore(NAMESPACE).get("resources", ClassResources.class);
    if (r != null) {
      SeleniumLifecycle.close(r.session());
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    ClassResources classResources = context.getStore(NAMESPACE).get("resources", ClassResources.class);
    SeleniumLifecycle.Scope scope = SeleniumLifecycle.openScope(classResources.session());
    context.getStore(NAMESPACE).put("methodResources", new MethodResources(scope));

    context.getTestClass().ifPresent(cls -> {
      String method = context.getTestMethod().map(java.lang.reflect.Method::getName).orElse(null);
      CurrentTest.set(new CurrentTest.Info(cls.getName(), method, context.getDisplayName()));
      TamashReport.setCurrentTest(method != null ? cls.getName() + "#" + method : cls.getName());
    });
  }

  @Override
  public void afterEach(ExtensionContext context) {
    CurrentTest.clear();
    com.vibetestq.qtpsudhakar.tamash.healer.HealCache.clear();
    com.vibetestq.qtpsudhakar.tamash.Tamash.clearHint();
    TamashReport.setCurrentTest(null);
    MethodResources r = context.getStore(NAMESPACE).get("methodResources", MethodResources.class);
    if (r != null) {
      SeleniumLifecycle.closeScope(r.scope());
    }
  }

  // ---- TestWatcher: attach heals to the native test result ----------------

  private static final Set<String> PUBLISHED = ConcurrentHashMap.newKeySet();

  @Override public void testSuccessful(ExtensionContext context) { publishHeals(context); }
  @Override public void testFailed(ExtensionContext context, Throwable cause) { publishHeals(context); }
  @Override public void testAborted(ExtensionContext context, Throwable cause) { publishHeals(context); }

  private void publishHeals(ExtensionContext context) {
    String id = context.getTestClass()
        .map(c -> c.getName() + context.getTestMethod().map(m -> "#" + m.getName()).orElse(""))
        .orElse(null);
    if (id == null || !PUBLISHED.add(id)) {
      return;
    }
    List<SelfHealingReport> mine = TamashHeals.forTest(id);
    if (mine.isEmpty()) {
      return;
    }
    try {
      context.publishReportEntry("tamash-self-healing", TamashHeals.summary(mine));
    } catch (Exception ignored) {
      // never fatal
    }
    try {
      context.publishFile("tamash-self-healing.json", MediaType.APPLICATION_JSON,
          path -> Files.writeString(path, TamashHeals.toJson(mine), StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // publishFile requires JUnit >= 5.12 and a live reporting context — degrade silently
    }
    for (SelfHealingReport r : mine) {
      if (!r.isHealed() && r.ariaSnapshotForReport != null) {
        try {
          context.publishFile("tamash-dom-" + r.getAction() + ".txt", MediaType.TEXT_PLAIN,
              path -> Files.writeString(path, r.ariaSnapshotForReport, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
          // see above
        }
      }
    }
  }

  // ---- parameter resolution ----------------------------------------------

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    Class<?> type = parameterContext.getParameter().getType();
    return type == WebDriver.class || type == JavascriptExecutor.class || type == TakesScreenshot.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    Class<?> type = parameterContext.getParameter().getType();
    SeleniumLifecycle.Scope scope = extensionContext.getStore(NAMESPACE)
        .get("methodResources", MethodResources.class).scope();
    if (type == WebDriver.class || type == JavascriptExecutor.class || type == TakesScreenshot.class) {
      return scope.driver();
    }
    throw new ParameterResolutionException("Unsupported parameter type: " + type);
  }
}
