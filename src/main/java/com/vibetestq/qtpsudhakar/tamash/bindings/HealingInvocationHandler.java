package com.vibetestq.qtpsudhakar.tamash.bindings;

import com.vibetestq.qtpsudhakar.tamash.CurrentTest;
import com.vibetestq.qtpsudhakar.tamash.healer.Healer;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Selenium counterpart of the Playwright port's {@code bindTargetActions} — one
 * {@link Proxy}/{@link InvocationHandler} intercepting {@code findElement}/{@code findElements} and
 * the interactive {@link WebElement} methods.
 *
 * <p>Healing model: a broken {@code findElement} throw is healed at find time — including inside a
 * {@code WebDriverWait} (the {@link com.vibetestq.qtpsudhakar.tamash.healer.HealCache} keeps a
 * wait's repeated polls to ~one heal). A {@code StaleElementReferenceException} at action time
 * re-finds with the original locator first, then heals; an interactability error runs action
 * recovery (opt-in).
 */
final class HealingInvocationHandler implements InvocationHandler {

  private static final Set<String> ELEMENT_ACTIONS = Set.of(
      "click", "submit", "sendKeys", "clear",
      "getText", "getAttribute", "getDomAttribute", "getDomProperty", "getCssValue",
      "isDisplayed", "isEnabled", "isSelected", "getTagName", "getAccessibleName", "getAriaRole", "getRect");

  private static final Set<String> NAV_TRACKED = Set.of("get", "to", "navigate");

  private final Object target;
  private final String kind;                 // driver | element
  private final By originatingBy;            // for an element: the By it was found with
  private final SearchContext parentContext; // for an element: where it was found
  private final Throwable callSite;
  private final List<By> frameChain;

  private HealingInvocationHandler(Object target, String kind, By originatingBy,
                                   SearchContext parentContext, Throwable callSite, List<By> frameChain) {
    this.target = target;
    this.kind = kind;
    this.originatingBy = originatingBy;
    this.parentContext = parentContext;
    this.callSite = callSite;
    this.frameChain = frameChain == null ? List.of() : frameChain;
  }

  // ---- factories -----------------------------------------------------

  private static final java.util.concurrent.atomic.AtomicBoolean IMPLICIT_WAIT_NOTED =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  static WebDriver wrapDriver(WebDriver driver) {
    if (driver == null) return null;
    Object raw = unwrap(driver);
    pinImplicitWait((WebDriver) raw);
    return (WebDriver) proxy(raw, new HealingInvocationHandler(raw, "driver", null, null, null, List.of()));
  }

  /** A high implicit wait makes a broken find block before the healer can run, and mixing implicit
   *  + explicit waits is a Selenium anti-pattern. Pin it to 0 unless the consumer opts out. */
  private static void pinImplicitWait(WebDriver driver) {
    String keep = com.vibetestq.qtpsudhakar.tamash.Env.get("TAMASH_KEEP_IMPLICIT_WAIT");
    if (keep != null && keep.equalsIgnoreCase("true")) {
      return;
    }
    try {
      driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
      if (IMPLICIT_WAIT_NOTED.compareAndSet(false, true)) {
        System.out.println("[tamash] implicit wait set to 0 for self-healing "
            + "(use explicit WebDriverWait; TAMASH_KEEP_IMPLICIT_WAIT=true to keep yours)");
      }
    } catch (Exception ignored) {
      // some drivers / stages don't allow it — not fatal
    }
  }

  static WebElement wrapElement(WebElement element, By originatingBy, SearchContext parent,
                                Throwable callSite, List<By> frameChain) {
    if (element == null) return null;
    Object raw = unwrap(element);
    return (WebElement) proxy(raw,
        new HealingInvocationHandler(raw, "element", originatingBy, parent, callSite, frameChain));
  }

  private static Object proxy(Object raw, HealingInvocationHandler handler) {
    Class<?>[] ifaces = allInterfaces(raw.getClass());
    return Proxy.newProxyInstance(HealingInvocationHandler.class.getClassLoader(), ifaces, handler);
  }

  private static Class<?>[] allInterfaces(Class<?> cls) {
    Set<Class<?>> set = new LinkedHashSet<>();
    for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
      collect(c, set);
    }
    if (WebDriver.class.isAssignableFrom(cls)) {
      set.add(WebDriver.class);
    } else {
      set.add(WebElement.class);
      // So Selenium's JSON encoder / Actions unwraps this proxy to the real element instead of
      // hard-casting to RemoteWebElement (which a Proxy can never satisfy).
      set.add(org.openqa.selenium.WrapsElement.class);
    }
    return set.toArray(new Class<?>[0]);
  }

  private static void collect(Class<?> cls, Set<Class<?>> set) {
    for (Class<?> i : cls.getInterfaces()) {
      if (set.add(i)) {
        collect(i, set);
      }
    }
  }

  @SuppressWarnings("unchecked")
  static <T> T unwrap(T maybeProxy) {
    if (maybeProxy != null && Proxy.isProxyClass(maybeProxy.getClass())) {
      InvocationHandler h = Proxy.getInvocationHandler(maybeProxy);
      if (h instanceof HealingInvocationHandler healing) {
        return (T) healing.target;
      }
    }
    return maybeProxy;
  }

  static WebDriver driverBehind(Object proxyOrRaw) {
    Object raw = unwrap(proxyOrRaw);
    if (raw instanceof WebDriver d) {
      return d;
    }
    if (raw instanceof org.openqa.selenium.WrapsDriver w) {
      return w.getWrappedDriver();
    }
    return null;
  }

  // ---- invoke -------------------------------------------------------

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String name = method.getName();

    // Object / identity / unwrap plumbing
    switch (name) {
      case "toString" -> { return "Tamash(" + target + ")"; }
      case "hashCode" -> { return System.identityHashCode(target); }
      case "equals" -> { return args != null && args.length == 1 && unwrap(args[0]) == target; }
      case "getWrappedElement" -> { return target; }
      case "getWrappedDriver" -> { return driverBehind(target); }
      default -> { }
    }

    // findElement(By) → heal on failure, re-wrap the result
    if ("findElement".equals(name) && args != null && args.length == 1 && args[0] instanceof By by) {
      return doFindElement(by);
    }
    if ("findElements".equals(name) && args != null && args.length == 1 && args[0] instanceof By by) {
      @SuppressWarnings("unchecked")
      List<WebElement> found = (List<WebElement>) invokeTarget(method, args);
      List<WebElement> wrapped = new ArrayList<>(found.size());
      for (WebElement e : found) {
        wrapped.add(wrapElement(e, by, (SearchContext) target, new Throwable(), frameChain));
      }
      return wrapped;
    }

    // driver navigation → tracked-only step for the report
    if ("driver".equals(kind) && NAV_TRACKED.contains(name)) {
      long start = System.nanoTime();
      try {
        Object r = invokeTarget(method, args);
        Steps.recordSimple(name, "driver " + name, args, (System.nanoTime() - start) / 1_000_000.0, null);
        return r;
      } catch (Throwable e) {
        Steps.recordSimple(name, "driver " + name, args, (System.nanoTime() - start) / 1_000_000.0, e);
        throw e;
      }
    }

    // interactive element action → intercept failure
    if ("element".equals(kind) && ELEMENT_ACTIONS.contains(name)) {
      return doElementAction(proxy, method, name, args);
    }

    return invokeTarget(method, args);
  }

  // ---- findElement healing ----------------------------------------

  private Object doFindElement(By by) throws Throwable {
    Throwable site = new Throwable();
    try {
      WebElement found = (WebElement) invokeTargetMethod("findElement", new Class<?>[]{By.class}, new Object[]{by});
      return wrapElement(found, by, (SearchContext) target, site, frameChain);
    } catch (Throwable error) {
      if (!isMissingElement(error) || !Healer.isHealingEnabled()) {
        throw error;
      }
      CallSiteInfo cs = resolveCallSite(site);
      // Asserting the element is ABSENT (invisibility wait, assertThrows(NoSuchElement…)) — a heal
      // would defeat the assertion. And HEALER_ASSERTIONS=strict: don't heal inside any assertion.
      if (cs.negative() || (cs.inAssertion() && "strict".equals(Healer.assertionMode()))) {
        throw error;
      }
      // Inside a WebDriverWait on a still-loading SPA a valid locator fails a poll or two then
      // resolves — don't pay for a snapshot/provider call on the first failure. A genuinely broken
      // locator fails every poll and heals a few polls in (~1.5s).
      boolean inWait = SourceLocations.calledFromWait(site);
      if (inWait && !com.vibetestq.qtpsudhakar.tamash.healer.HealCache.everHealed(by)
          && com.vibetestq.qtpsudhakar.tamash.healer.HealCache.recordFailing(by) <= 3) {
        throw error;
      }
      Healer.HealContext ctx = new Healer.HealContext();
      ctx.action = null;
      ctx.kind = kind;
      ctx.inWait = inWait;
      ctx.inAssertion = cs.inAssertion();
      ctx.description = describeFrom(cs.rawName(), by);
      ctx.error = error;
      ctx.driver = "driver".equals(kind) ? (WebDriver) target : HealingInvocationHandler.driverBehind(target);
      ctx.by = by;
      ctx.originalByString = by.toString();
      ctx.method = null;
      ctx.args = new Object[0];
      ctx.sourceLocation = cs.location();
      ctx.rawVariableName = cs.rawName();
      ctx.enclosingClass = cs.simpleClassName();
      ctx.frameChain = frameChain;
      attachTest(ctx);
      Healer.HealResult healing = Healer.healActionFailure(ctx);
      if (healing.recovered() && healing.result() instanceof WebElement healed) {
        return wrapElement(healed, by, (SearchContext) target, site, frameChain);
      }
      throw error;
    }
  }

  // ---- element action healing -----------------------------------

  private Object doElementAction(Object proxy, Method method, String name, Object[] args) throws Throwable {
    long start = System.nanoTime();
    try {
      Object result = invokeTarget(method, args);
      Steps.recordSuccess(name, describeSelf(), originatingBy, args, result, (System.nanoTime() - start) / 1_000_000.0);
      return result;
    } catch (Throwable error) {
      double elapsed = (System.nanoTime() - start) / 1_000_000.0;
      boolean stale = Healer.isStaleFailure(error);
      boolean interactability = Healer.isActionabilityFailure(error);
      if ((!stale && !interactability) || !Healer.isHealingEnabled() || originatingBy == null) {
        throw error;
      }

      // cheap first move for a stale element: re-find with the same locator and retry once
      if (stale && parentContext != null) {
        try {
          WebElement fresh = unwrap(parentContext).findElement(originatingBy);
          Object r = method.invoke(fresh, args);
          Steps.recordSuccess(name, describeSelf(), originatingBy, args, r, elapsed);
          return r;
        } catch (Throwable ignored) {
          // fall through to full heal
        }
      }

      // Resolve against the current stack (where the action was invoked from — an assert / a util)
      // as well as the captured find site.
      CallSiteInfo cs = resolveCallSite(new Throwable());
      if (cs.rawName() == null && callSite != null) {
        CallSiteInfo atFind = resolveCallSite(callSite);
        if (atFind.rawName() != null) {
          cs = new CallSiteInfo(atFind.location(), atFind.simpleClassName(), atFind.rawName(),
              cs.inAssertion(), cs.negative());
        }
      }
      if (cs.negative() || (cs.inAssertion() && "strict".equals(Healer.assertionMode()))) {
        throw error;
      }
      Healer.HealContext ctx = new Healer.HealContext();
      ctx.action = name;
      ctx.kind = kind;
      ctx.inAssertion = cs.inAssertion();
      ctx.description = describeFrom(cs.rawName(), originatingBy);
      ctx.error = error;
      ctx.driver = HealingInvocationHandler.driverBehind(target);
      ctx.by = originatingBy;
      ctx.originalByString = originatingBy.toString();
      ctx.method = method;
      ctx.args = args;
      ctx.sourceLocation = cs.location();
      ctx.rawVariableName = cs.rawName();
      ctx.enclosingClass = cs.simpleClassName();
      ctx.frameChain = frameChain;
      attachTest(ctx);
      Healer.HealResult healing = Healer.healActionFailure(ctx);
      Steps.recordFailure(name, ctx.description, originatingBy, args, elapsed, healing.report());
      if (healing.recovered()) {
        return healing.result();
      }
      throw error;
    }
  }

  // ---- helpers -----------------------------------------------------

  private void attachTest(Healer.HealContext ctx) {
    CurrentTest.Info ti = CurrentTest.get();
    if (ti != null && ti.testClass() != null) {
      ctx.testSelector = ti.testMethod() != null ? ti.testClass() + "#" + ti.testMethod() : ti.testClass();
      ctx.testTitle = ti.displayName();
    }
  }

  private static boolean isMissingElement(Throwable t) {
    String cn = t.getClass().getSimpleName();
    return cn.equals("NoSuchElementException") || cn.equals("InvalidSelectorException")
        || cn.equals("StaleElementReferenceException");
  }


  private String describeSelf() {
    return originatingBy != null ? originatingBy.toString() : null;
  }

  /** One consumer call site: its location, the class it's in, the resolved locator identifier
   *  (or null), and whether it sits in an assertion / an "assert absent" context. */
  record CallSiteInfo(String location, String simpleClassName, String rawName,
                      boolean inAssertion, boolean negative) {}

  /**
   * Walks up to 3 consumer frames. The assertion / negative flags are OR-ed across all of them
   * (an assert or a {@code WebUtil.waitInvisible(...)} may be a frame or two above the raw
   * {@code findElement}). The locator name is taken from the first frame that yields one — an
   * {@code X = ...} assignment, a bare reference ({@code findElement(loginButton)},
   * {@code loginButton.click()}, a wait condition), or an argument passed into a util
   * ({@code WebUtil.click(driver, loginButton)}).
   */
  static CallSiteInfo resolveCallSite(Throwable site) {
    List<SourceLocations.Caller> chain = SourceLocations.resolveConsumerChain(site, 3);
    boolean negative = false;
    boolean inAssertion = false;
    for (SourceLocations.Caller c : chain) {
      if (SourceLocations.isNegativeFindContext(c.location(), site)) negative = true;
      if (SourceLocations.isAssertionCallSite(c.location())) inAssertion = true;
    }
    for (SourceLocations.Caller c : chain) {
      String n = SourceLocations.extractVariableName(c.location());
      if (n == null) n = SourceLocations.extractLocatorReference(c.location());
      if (n == null) n = SourceLocations.extractArgIdentifier(c.location());
      if (n == null) n = SourceLocations.extractLocatorishToken(c.location());
      if (n != null) {
        return new CallSiteInfo(c.location(), c.simpleClassName(), n, inAssertion, negative);
      }
    }
    SourceLocations.Caller first = chain.isEmpty() ? null : chain.get(0);
    return new CallSiteInfo(first != null ? first.location() : null,
        first != null ? first.simpleClassName() : null, null, inAssertion, negative);
  }

  /** Façade for the {@code @FindBy} nested-find path: the decoded call-site description. */
  static String describeCallSite(Throwable site, By by) {
    return describeFrom(resolveCallSite(site).rawName(), by);
  }

  /** The human label from a resolved identifier, decoded — else the raw selector text. */
  private static String describeFrom(String rawName, By by) {
    SourceLocations.Decoded d = rawName != null ? SourceLocations.decodeVariableName(rawName) : null;
    if (d != null) {
      return d.typeHint() != null ? d.name() + " (" + d.typeHint() + ")" : d.name();
    }
    if (rawName != null) {
      return rawName;
    }
    return by != null ? by.toString() : null;
  }

  private Object invokeTarget(Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause() != null ? e.getCause() : e;
    }
  }

  private Object invokeTargetMethod(String name, Class<?>[] sig, Object[] args) throws Throwable {
    try {
      Method m = target.getClass().getMethod(name, sig);
      m.setAccessible(true);
      return m.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause() != null ? e.getCause() : e;
    }
  }
}
