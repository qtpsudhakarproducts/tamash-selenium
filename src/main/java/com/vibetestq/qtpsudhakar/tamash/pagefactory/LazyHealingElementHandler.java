package com.vibetestq.qtpsudhakar.tamash.pagefactory;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.support.pagefactory.ElementLocator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The healing counterpart of Selenium's own {@code LocatingElementHandler} — backs the proxy
 * {@link TamashFieldDecorator} puts behind a {@code @FindBy} field. Resolution stays lazy (via the
 * wrapped {@link ElementLocator}, so {@code @CacheLookup} / {@code AjaxElementLocatorFactory} still
 * apply); if resolution or the action fails, the healer runs — with the action name already known,
 * so role inference and the full ref/derive path work exactly as for a direct {@code findElement}.
 */
final class LazyHealingElementHandler implements InvocationHandler {

  private final WebDriver driver;
  private final ElementLocator locator;
  private final By by;
  private final String description;
  private final String sourceLocation;
  private final String rawFieldName;
  private final String enclosingClass;

  LazyHealingElementHandler(WebDriver driver, ElementLocator locator, By by, String description,
                            String sourceLocation, String rawFieldName, String enclosingClass) {
    this.driver = driver;
    this.locator = locator;
    this.by = by;
    this.description = description;
    this.sourceLocation = sourceLocation;
    this.rawFieldName = rawFieldName;
    this.enclosingClass = enclosingClass;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String name = method.getName();
    switch (name) {
      case "toString" -> { return "TamashFindBy(" + by + ")"; }
      case "hashCode" -> { return by.hashCode(); }
      case "equals" -> { return proxy == (args != null ? args[0] : null); }
      default -> { }
    }
    if (name.equals("getWrappedElement") && method.getDeclaringClass() == WrapsElement.class) {
      return resolve(name, method, args);
    }

    Throwable callSite = new Throwable();
    WebElement element;
    try {
      element = locator.findElement();
    } catch (RuntimeException findError) {
      if (!isMissing(findError) || !Healer.isHealingEnabled() || skipForAssertion(callSite)) {
        throw findError;
      }
      // A @FindBy field polled inside a WebDriverWait on a still-loading SPA fails a poll or two then
      // resolves — defer the first few failures rather than pay for a snapshot/provider call each time.
      if (com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.calledFromWait(callSite)
          && !com.vibetestq.qtpsudhakar.tamash.healer.HealCache.everHealed(by)
          && com.vibetestq.qtpsudhakar.tamash.healer.HealCache.recordFailing(by) <= 3) {
        throw findError;
      }
      Healer.HealResult healed = heal(name, method, args, findError, callSite);
      if (healed.recovered()) {
        return healed.result();
      }
      throw findError;
    }

    // Nested find on this @FindBy container: container.findElement(childBy) — heal the CHILD locator.
    if (("findElement".equals(name) || "findElements".equals(name))
        && args != null && args.length == 1 && args[0] instanceof By childBy) {
      return nestedFind(name, element, childBy, callSite);
    }

    try {
      return invokeOn(element, method, args);
    } catch (RuntimeException actionError) {
      if ((!Healer.isStaleFailure(actionError) && !Healer.isActionabilityFailure(actionError))
          || !Healer.isHealingEnabled() || skipForAssertion(callSite)) {
        throw actionError;
      }
      Healer.HealResult healed = heal(name, method, args, actionError, callSite);
      if (healed.recovered()) {
        return healed.result();
      }
      throw actionError;
    }
  }

  @SuppressWarnings("unchecked")
  private Object nestedFind(String name, WebElement container, By childBy, Throwable callSite) throws Throwable {
    try {
      Object r = name.equals("findElements")
          ? container.findElements(childBy)
          : container.findElement(childBy);
      return r;
    } catch (RuntimeException e) {
      if ("findElements".equals(name) || !isMissing(e) || !Healer.isHealingEnabled()
          || skipForAssertion(callSite)) {
        throw e;
      }
      Healer.HealContext ctx = new Healer.HealContext();
      ctx.action = null;
      ctx.kind = "element";
      ctx.description = com.vibetestq.qtpsudhakar.tamash.bindings.Bindings
          .describeCallSite(callSite, childBy);
      ctx.error = e;
      ctx.driver = driver;
      ctx.by = childBy;
      ctx.originalByString = childBy.toString();
      ctx.method = null;
      ctx.args = new Object[0];
      ctx.sourceLocation = com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.resolveCallerLocation(callSite);
      ctx.rawVariableName = ctx.description;
      ctx.inAssertion = assertionContext(callSite);
      ctx.inWait = com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.calledFromWait(callSite);
      ctx.frameChain = java.util.List.of();
      com.vibetestq.qtpsudhakar.tamash.CurrentTest.Info ti =
          com.vibetestq.qtpsudhakar.tamash.CurrentTest.get();
      if (ti != null && ti.testClass() != null) {
        ctx.testSelector = ti.testMethod() != null ? ti.testClass() + "#" + ti.testMethod() : ti.testClass();
        ctx.testTitle = ti.displayName();
      }
      Healer.HealResult healed = Healer.healActionFailure(ctx);
      if (healed.recovered() && healed.result() instanceof WebElement w) {
        return w;
      }
      throw e;
    }
  }

  /** True when the field is used inside an "assert absent" check, or HEALER_ASSERTIONS=strict and
   *  it's an assertion call site — a heal would be wrong / unwanted. */
  private static boolean skipForAssertion(Throwable callSite) {
    return negativeOrStrictAssertion(callSite);
  }

  private static boolean assertionContext(Throwable callSite) {
    for (var c : com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.resolveConsumerChain(callSite, 3)) {
      if (com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.isAssertionCallSite(c.location())) {
        return true;
      }
    }
    return false;
  }

  private static boolean negativeOrStrictAssertion(Throwable callSite) {
    for (var c : com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.resolveConsumerChain(callSite, 3)) {
      if (com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.isNegativeFindContext(c.location(), callSite)) {
        return true;
      }
    }
    return "strict".equals(Healer.assertionMode()) && assertionContext(callSite);
  }

  private WebElement resolve(String name, Method method, Object[] args) throws Throwable {
    Throwable callSite = new Throwable();
    try {
      return locator.findElement();
    } catch (RuntimeException e) {
      if (!isMissing(e) || !Healer.isHealingEnabled() || skipForAssertion(callSite)) {
        throw e;
      }
      Healer.HealResult healed = heal(name, null, null, e, callSite);
      if (healed.recovered() && healed.result() instanceof WebElement w) {
        return w;
      }
      throw e;
    }
  }

  private Healer.HealResult heal(String action, Method method, Object[] args, Throwable error, Throwable callSite) {
    Healer.HealContext ctx = new Healer.HealContext();
    ctx.action = method == null ? null : action;
    ctx.kind = "element";
    ctx.description = description;
    ctx.error = error;
    ctx.driver = driver;
    ctx.by = by;
    ctx.originalByString = by.toString();
    ctx.method = method;
    ctx.args = args == null ? new Object[0] : args;
    ctx.sourceLocation = sourceLocation;
    ctx.rawVariableName = rawFieldName;
    ctx.enclosingClass = enclosingClass;
    ctx.inAssertion = assertionContext(callSite);
    ctx.inWait = com.vibetestq.qtpsudhakar.tamash.bindings.SourceLocations.calledFromWait(callSite);
    ctx.frameChain = java.util.List.of();
    CurrentTest.Info ti = CurrentTest.get();
    if (ti != null && ti.testClass() != null) {
      ctx.testSelector = ti.testMethod() != null ? ti.testClass() + "#" + ti.testMethod() : ti.testClass();
      ctx.testTitle = ti.displayName();
    }
    return Healer.healActionFailure(ctx);
  }

  private static Object invokeOn(WebElement element, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(element, args);
    } catch (InvocationTargetException e) {
      throw e.getCause() != null ? e.getCause() : e;
    }
  }

  private static boolean isMissing(Throwable t) {
    String cn = t.getClass().getSimpleName();
    return cn.equals("NoSuchElementException") || cn.equals("StaleElementReferenceException")
        || cn.equals("InvalidSelectorException");
  }
}
