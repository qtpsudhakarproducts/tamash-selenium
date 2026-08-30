package io.github.qtpsudhakarproducts.tamash.healer;

import io.github.qtpsudhakarproducts.tamash.Env;
import io.github.qtpsudhakarproducts.tamash.healer.providers.AiSuggestion;
import io.github.qtpsudhakarproducts.tamash.healer.providers.HealProvider;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderFactory;
import io.github.qtpsudhakarproducts.tamash.healer.providers.ProviderResult;
import io.github.qtpsudhakarproducts.tamash.healer.providers.SuggestElementFromImageInput;
import io.github.qtpsudhakarproducts.tamash.healer.providers.SuggestSelectorInput;
import io.github.qtpsudhakarproducts.tamash.healer.providers.TokenUsage;
import io.github.qtpsudhakarproducts.tamash.healer.providers.VisionPoint;
import io.github.qtpsudhakarproducts.tamash.healer.providers.VisionProviderResult;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Port of src/healer/index.ts — the runtime self-healing orchestration, Selenium edition. */
public final class Healer {
  private Healer() {}

  public static final double DEFAULT_TIMEOUT_MS = 10000.0;

  private static final List<SelfHealingReport> REPORTS = new CopyOnWriteArrayList<>();
  private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger();
  private static final ThreadLocal<Boolean> HEALING_IN_PROGRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);

  public record HealResult(SelfHealingReport report, boolean recovered, Object result) {}

  /** Everything {@code healActionFailure} needs. */
  public static final class HealContext {
    public String action;            // click | sendKeys | clear | submit | getText | … | null (bare find failure)
    public String kind;              // driver | element
    public String description;
    public Throwable error;
    public WebDriver driver;
    public By by;                    // the original By that failed
    public String originalByString;
    public Method method;            // the failed WebElement method (null when the failure was at find time)
    public Object[] args;
    public String sourceLocation;    // "file:line" from resolveCallerLocation, or null
    public String rawVariableName;   // the undecoded locator variable / @FindBy field name, or null
    public String enclosingClass;    // simple name of the Page Object / test class, or null
    public boolean inAssertion;      // call site looks like an assertion (HEALER_ASSERTIONS=warn surfaces it)
    public boolean inWait;           // call site is inside a WebDriverWait/FluentWait poll — stay quiet on non-heals
    public List<By> frameChain;
    public String testSelector;
    public String testTitle;
  }

  // ---- config -----------------------------------------------------------

  public static boolean isHealingEnabled() {
    String value = Env.get("HEALER_ENABLED");
    if (value == null) return true;
    String n = value.trim().toLowerCase();
    return !n.equals("false") && !n.equals("0");
  }

  public static boolean isActionRecoveryEnabled() {
    String v = Env.get("HEALER_ACTION_RECOVERY_ENABLED");
    return v != null && v.trim().equalsIgnoreCase("true");
  }

  /** {@code heal} (default) | {@code warn} (heal but flag the test) | {@code strict} (don't heal a
   *  locator resolved inside an assertion — let it fail natively). */
  public static String assertionMode() {
    String v = Env.get("HEALER_ASSERTIONS");
    if (v == null) return "heal";
    String n = v.trim().toLowerCase();
    return (n.equals("warn") || n.equals("strict")) ? n : "heal";
  }

  private static final Set<String> ASSERTION_HEALED_TESTS =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static final java.util.concurrent.atomic.AtomicBoolean ASSERTION_HOOK =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  static void noteAssertionHeal(String testSelector) {
    ASSERTION_HEALED_TESTS.add(testSelector != null ? testSelector : "(unattributed)");
    if (ASSERTION_HOOK.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(new Thread(Healer::printAssertionHealSummary));
    }
  }

  /** Printed at JVM shutdown (and callable from the report listeners) when
   *  {@code HEALER_ASSERTIONS=warn} and at least one assertion locator was healed. */
  public static void printAssertionHealSummary() {
    if (ASSERTION_HEALED_TESTS.isEmpty()) {
      return;
    }
    System.err.println("[tamash] " + ASSERTION_HEALED_TESTS.size()
        + " test(s) had a locator healed inside an assertion (HEALER_ASSERTIONS=warn):");
    ASSERTION_HEALED_TESTS.stream().sorted().forEach(t -> System.err.println("  - " + t));
    System.err.println("  Review these — a wrong heal in an assertion can hide a real bug. "
        + "Run with HEALER_ASSERTIONS=strict to fail instead of heal.");
  }

  static double effectiveTimeout() {
    String v = Env.get("TAMASH_ACTION_TIMEOUT_MS");
    if (v != null) {
      try {
        double d = Double.parseDouble(v.trim());
        if (d > 0) return d;
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return DEFAULT_TIMEOUT_MS;
  }

  // ---- error classification ------------------------------------------

  static String normalizeError(Throwable error) {
    if (error == null) return "Unknown action failure";
    String msg = error.getMessage() != null ? error.getMessage() : error.toString();
    int nl = msg.indexOf('\n');
    return nl == -1 ? msg : msg.substring(0, nl).trim();
  }

  /** Element found, but the interaction itself couldn't complete — not a selector problem. */
  public static boolean isActionabilityFailure(Throwable error) {
    if (error == null) return false;
    String cn = error.getClass().getSimpleName();
    return cn.equals("ElementNotInteractableException")
        || cn.equals("ElementClickInterceptedException")
        || cn.equals("InvalidElementStateException");
  }

  public static boolean isStaleFailure(Throwable error) {
    return error != null && error.getClass().getSimpleName().equals("StaleElementReferenceException");
  }

  // ---- page context ------------------------------------------------

  private static PageContext resolvePageContext(HealContext c) {
    return c.frameChain != null && !c.frameChain.isEmpty()
        ? PageContext.of(c.driver, c.frameChain)
        : PageContext.of(c.driver);
  }

  private static String captureSnapshot(PageContext ctx) {
    if (ctx == null) return null;
    try {
      ctx.enterFrame();
      return DomSnapshot.capture(ctx.driver());
    } catch (Exception e) {
      return null;
    } finally {
      ctx.exitFrame();
    }
  }

  // ---- locator construction --------------------------------------

  record Built(WebElement element, By by, AiSuggestion resolvedSuggestion) {}

  private static Built buildLocatorFromSuggestion(PageContext ctx, AiSuggestion s, String action) {
    By by = DurableLocator.toBy(s);
    if (by == null) {
      // label-style fallback: infer a role and try a near-anchor
      String inferred = DurableLocator.inferRoleFromAction(action);
      if (inferred == null || s.getText() == null) {
        return null;
      }
      by = DurableLocator.toBy(AiSuggestion.near(s.getText(), inferred, 1));
      s = AiSuggestion.near(s.getText(), inferred, 1);
      if (by == null) return null;
    }
    ctx.enterFrame();
    try {
      WebElement el = ctx.findOrNull(by);
      return el == null ? null : new Built(el, by, s);
    } finally {
      ctx.exitFrame();
    }
  }

  static String describeSuggestion(AiSuggestion s) {
    if ("ref".equals(s.getStrategy())) {
      return "ref:" + s.getRef() + " (transient — not persisted)";
    }
    return DurableLocator.generateReplacementCall(s);
  }

  // ---- reflected replay ------------------------------------------

  static final Set<String> REPLAYABLE_ACTIONS = Set.of(
      "click", "submit", "sendKeys", "clear",
      "getText", "getAttribute", "getDomAttribute", "getDomProperty", "getCssValue",
      "isDisplayed", "isEnabled", "isSelected", "getTagName", "getAccessibleName", "getAriaRole", "getRect");

  private static Object replayViaMethod(WebElement element, Method method, Object[] args) throws Throwable {
    if (method == null) {
      return element; // find-time heal: "replaying" just means handing back the freshly-found element
    }
    if (!REPLAYABLE_ACTIONS.contains(method.getName())) {
      throw new UnsupportedOperationException(
          "Action \"" + method.getName() + "\" cannot be safely replayed by the self-healer.");
    }
    try {
      return method.invoke(element, args);
    } catch (InvocationTargetException e) {
      throw e.getCause() != null ? e.getCause() : e;
    }
  }

  // ---- durable locator derivation --------------------------------

  public record DerivedDurableLocator(WebElement element, By by, AiSuggestion suggestion,
                                      String initialSelector, boolean needsReview, String reviewNote) {}

  record TreeContext(String snapshot, List<String> candidateRefs, String aiNearbyRef, String aiNearbyText) {}

  static DerivedDurableLocator deriveDurableLocator(PageContext ctx, WebElement anchor, String action,
                                                    TreeContext tree, String initialSelector) {
    // 1. does the element stand on its own?
    ctx.enterFrame();
    try {
      AiSuggestion own = DurableLocator.deriveSuggestionFromElement(ctx.driver(), anchor);
      if (own != null) {
        By by = DurableLocator.toBy(own);
        WebElement candidate = ctx.findOrNull(by);
        if (candidate != null && DurableLocator.sameElement(ctx.driver(), candidate, anchor)) {
          return new DerivedDurableLocator(candidate, by, own, initialSelector, false, null);
        }
      }
    } catch (Exception ignored) {
      // fall through to structural anchoring
    } finally {
      ctx.exitFrame();
    }

    // 2. structural: anchor on nearby text (near / adjacent), verified by DOM identity
    if (tree != null) {
      List<DurableLocator.AriaAiNode> nodes = DurableLocator.parseAriaAiTree(tree.snapshot());
      for (String ref : tree.candidateRefs()) {
        DurableLocator.AriaAiNode targetNode =
            nodes.stream().filter(n -> ref.equals(n.ref())).findFirst().orElse(null);
        String inferredRole = (targetNode != null && targetNode.role() != null && !targetNode.role().equals("generic"))
            ? targetNode.role() : DurableLocator.inferRoleFromAction(action);
        if (inferredRole == null) {
          continue;
        }

        if (tree.aiNearbyRef() != null) {
          DurableLocator.AdjacentBranchPath bp = DurableLocator.findAdjacentBranchPath(nodes, ref, tree.aiNearbyRef());
          DurableLocator.AriaAiNode anchorNode =
              nodes.stream().filter(n -> tree.aiNearbyRef().equals(n.ref())).findFirst().orElse(null);
          String anchorText = anchorNode == null ? null
              : (anchorNode.text() != null ? anchorNode.text() : anchorNode.name());
          if (bp != null && anchorText != null) {
            AiSuggestion adj = AiSuggestion.adjacent(anchorText, inferredRole,
                bp.anchorClimbLevels() == 0 ? null : bp.anchorClimbLevels());
            DerivedDurableLocator d = verifyStructural(ctx, adj, anchor, initialSelector,
                "Durable selector anchors on the adjacent label — verify if the page layout changes.");
            if (d != null) return d;
          }
        }

        List<String> texts = new ArrayList<>();
        if (tree.aiNearbyText() != null) {
          texts.add(tree.aiNearbyText());
        }
        for (DurableLocator.SiblingAnchorCandidate c : DurableLocator.findSiblingAnchorTexts(nodes, ref)) {
          if (!c.text().equals(tree.aiNearbyText())) {
            texts.add(c.text());
          }
        }
        for (String t : texts) {
          for (int levels : new int[]{1, 2}) {
            AiSuggestion near = AiSuggestion.near(t, inferredRole, levels);
            DerivedDurableLocator d = verifyStructural(ctx, near, anchor, initialSelector,
                "Durable selector anchors on nearby text — verify if the page layout changes.");
            if (d != null) return d;
          }
        }
      }
    }
    return null;
  }

  private static DerivedDurableLocator verifyStructural(PageContext ctx, AiSuggestion s, WebElement anchor,
                                                        String initialSelector, String note) {
    By by = DurableLocator.toBy(s);
    if (by == null) return null;
    ctx.enterFrame();
    try {
      List<WebElement> matches = ctx.findAll(by);
      if (matches.size() == 1 && DurableLocator.sameElement(ctx.driver(), matches.get(0), anchor)) {
        return new DerivedDurableLocator(matches.get(0), by, s, initialSelector, true, note);
      }
    } catch (Exception ignored) {
      // shape doesn't apply
    } finally {
      ctx.exitFrame();
    }
    return null;
  }

  /** Public counterpart backing {@code Bindings.getDurable}. */
  public static By getDurableLocator(WebDriver driver, By broken, String action, List<By> frameChain) {
    PageContext ctx = frameChain != null && !frameChain.isEmpty()
        ? PageContext.of(driver, frameChain) : PageContext.of(driver);
    WebElement anchor;
    ctx.enterFrame();
    try {
      anchor = ctx.find(broken);
    } catch (Exception e) {
      throw new RuntimeException("getDurable(): the given locator does not resolve to an element.");
    } finally {
      ctx.exitFrame();
    }
    String snapshot = captureSnapshot(ctx);
    TreeContext tree = null;
    if (snapshot != null) {
      String refAttr = attrSafe(anchor, DurableLocator.REF_ATTRIBUTE);
      if (refAttr != null) {
        tree = new TreeContext(snapshot, List.of(refAttr), null, null);
      }
    }
    DerivedDurableLocator derived = deriveDurableLocator(ctx, anchor, action == null ? "" : action, tree,
        broken.toString());
    if (derived == null) {
      throw new RuntimeException("getDurable(): could not derive a durable locator for this element.");
    }
    return derived.by();
  }

  private static String attrSafe(WebElement el, String name) {
    try { return el.getDomAttribute(name); } catch (Exception e) { return null; }
  }

  // ---- failure-stage messages ----------------------------------

  static final Map<String, String> FAILURE_STAGE_MESSAGES = new LinkedHashMap<>();
  static {
    FAILURE_STAGE_MESSAGES.put("disabled", "Healing is turned off (HEALER_ENABLED=false).");
    FAILURE_STAGE_MESSAGES.put("reentrant",
        "Blocked by the recursion guard (this failure happened while replaying an already-healed locator).");
    FAILURE_STAGE_MESSAGES.put("not-a-selector-issue",
        "The element was found, but the action itself could not complete — not a selector problem, so self-healing was not attempted.");
    FAILURE_STAGE_MESSAGES.put("no_snapshot", "Could not capture the page's DOM snapshot to search.");
    FAILURE_STAGE_MESSAGES.put("recently_declined",
        "A heal was just attempted for this locator and declined; the page hasn't changed since, so it wasn't retried (keeps healing inside a WebDriverWait cheap).");
    FAILURE_STAGE_MESSAGES.put("no_provider", "No AI provider is configured (missing API key/model).");
    FAILURE_STAGE_MESSAGES.put("provider_error", "The AI call itself failed or returned nothing usable — see the console log for detail.");
    FAILURE_STAGE_MESSAGES.put("ai_declined", "The AI found nothing in the snapshot plausibly matching the description.");
    FAILURE_STAGE_MESSAGES.put("unbuildable_suggestion", "The AI's suggested strategy couldn't be turned into a locator.");
    FAILURE_STAGE_MESSAGES.put("replay_failed", "The AI suggested a replacement locator, but acting on it failed too.");
    FAILURE_STAGE_MESSAGES.put("vision_provider_error", "The vision-capable AI call itself failed or returned nothing usable.");
    FAILURE_STAGE_MESSAGES.put("vision_declined", "The AI found nothing in the screenshot plausibly matching the description.");
    FAILURE_STAGE_MESSAGES.put("vision_unresolvable", "The AI pointed at a location in the screenshot, but no real element could be resolved there.");
    FAILURE_STAGE_MESSAGES.put("vision_replay_failed", "The AI located a visual match, but acting on it failed too.");
    FAILURE_STAGE_MESSAGES.put("action_recovery_declined", "The element was found, but none of the known recovery tactics would plausibly help.");
    FAILURE_STAGE_MESSAGES.put("action_recovery_failed", "A recovery tactic was attempted, but the action still could not be completed.");
  }

  // ---- main entry ---------------------------------------------

  public static HealResult healActionFailure(HealContext c) {
    // The healer's internal finds + DOM-identity checks must run on un-proxied Selenium objects.
    c.driver = io.github.qtpsudhakarproducts.tamash.bindings.Bindings.unwrap(c.driver);
    // An explicit Tamash.hint(...) wins over the automatic call-site decode.
    String hint = io.github.qtpsudhakarproducts.tamash.Tamash.currentHint();
    if (hint != null && !hint.isBlank()) {
      c.description = hint;
      c.rawVariableName = hint;
    }
    String reason = normalizeError(c.error);
    Object[] callArgs = c.args != null ? c.args : new Object[0];
    boolean healingEnabled = isHealingEnabled();
    boolean reentrant = HEALING_IN_PROGRESS.get();
    boolean actionabilityFailure = isActionabilityFailure(c.error) && !isActionRecoveryEnabled();
    boolean attemptHealing = healingEnabled && !reentrant && !actionabilityFailure;

    String failureStage;
    if (!healingEnabled) failureStage = "disabled";
    else if (reentrant) failureStage = "reentrant";
    else if (actionabilityFailure) failureStage = "not-a-selector-issue";
    else failureStage = "no_snapshot";

    double timeoutMs = effectiveTimeout();
    PageContext ctx = attemptHealing ? resolvePageContext(c) : null;

    // Heal cache keys: page for the positive (run-lifetime) cache, a coarse DOM fingerprint for
    // the negative (short-TTL) cache — the two things that keep healing inside a WebDriverWait cheap.
    String pageKey = pageKeyOf(c.driver);
    String domKey = domKeyOf(c.driver);
    boolean negativeSkip = attemptHealing && c.by != null
        && HealCache.positive(c.by, pageKey) == null && HealCache.recentlyDeclined(c.by, domKey);

    SelfHealingReport report = new SelfHealingReport();
    report.action = c.action != null ? c.action : "findElement";
    report.kind = c.kind;
    report.description = c.description;
    report.reason = reason;
    report.sourceLocation = c.sourceLocation;
    report.testSelector = c.testSelector;

    Recovery healing = null;
    TokenUsage usage = null;
    String suggestedSelector = null;
    String providerName = null;
    boolean usedVision = false;
    boolean usedActionRecovery = false;
    boolean usedCache = false;
    AiSuggestion capturedSuggestion = null;
    String initialSelector = c.originalByString;
    Boolean needsReview = null;
    String reviewNote = null;
    List<HealAttempt> attempts = report.attempts;
    String snapshot = null;

    boolean guardSet = attemptHealing && !Boolean.TRUE.equals(HEALING_IN_PROGRESS.get());
    if (guardSet) {
      HEALING_IN_PROGRESS.set(Boolean.TRUE);
    }
    try {
      Replayer plainReplay = el -> replayViaMethod(el, c.method, callArgs);

      // --- pure action-recovery path (element found, interaction blocked) ---
      if (attemptHealing && isActionabilityFailure(c.error) && c.method != null) {
        WebElement current = safeFind(ctx, c.by);
        if (current != null) {
          HealProvider provider = ProviderFactory.getHealProvider();
          if (provider != null) {
            providerName = provider.getName();
            ActionRecovery.Outcome ar = ActionRecovery.tryActionRecovery(provider, c.driver, current, c.action,
                callArgs, reason, timeoutMs, plainReplay);
            usage = ar.usage();
            usedActionRecovery = true;
            if (ar.healing() != null) {
              healing = new Recovery(ar.healing().provider(), ar.healing().warning(), null, ar.healing().result());
              failureStage = null;
              attempts.add(HealAttempt.of("action-recovery").provider(provider.getName()).succeeded(true));
            } else {
              failureStage = ar.stage();
              attempts.add(HealAttempt.of("action-recovery").provider(provider.getName()).succeeded(false)
                  .stage(ar.stage()).error(FAILURE_STAGE_MESSAGES.get(ar.stage())));
            }
          } else {
            failureStage = "no_provider";
          }
        }
      }

      // --- in-memory positive cache (this run): a selector already healed for this locator+page ---
      if (attemptHealing && healing == null && ctx != null && c.by != null) {
        HealCache.Hit hit = HealCache.positive(c.by, pageKey);
        if (hit != null) {
          WebElement el = safeFind(ctx, hit.healedBy());
          if (el != null) {
            try {
              Object result = plainReplay.replay(el);
              suggestedSelector = hit.describedAs();
              capturedSuggestion = hit.suggestion() != null && hit.suggestion().isPersistable() ? hit.suggestion() : null;
              usedCache = true;
              healing = new Recovery("cache",
                  "Recovered using a selector healed earlier this run (" + hit.describedAs() + ") — no snapshot, no AI.",
                  hit.describedAs(), result);
              failureStage = null;
              attempts.add(HealAttempt.of("cache").succeeded(true).suggested(hit.describedAs()));
            } catch (Throwable stale) {
              attempts.add(HealAttempt.of("cache").succeeded(false).suggested(hit.describedAs()).error(normalizeError(stale)));
            }
          }
        }
      }

      // --- negative cache: a heal was just declined for this exact DOM state — don't retry now ---
      if (attemptHealing && healing == null && negativeSkip) {
        failureStage = "recently_declined";
        attemptHealing = false;
      }

      // --- disk cache (cross-run): a previously-confirmed selector for this exact source line ---
      if (attemptHealing && healing == null && ctx != null && c.sourceLocation != null) {
        HealLog.Cached cached = HealLog.findCachedSuggestion(c.sourceLocation);
        // Guard against a shared source line (e.g. a WebUtil helper) reusing another locator's heal.
        if (cached != null && cached.initialSelector() != null && c.originalByString != null
            && !cached.initialSelector().equals(c.originalByString)) {
          cached = null;
        }
        if (cached != null) {
          Built built = buildLocatorFromSuggestion(ctx, cached.suggestion(), c.action);
          if (built != null) {
            String desc = describeSuggestion(built.resolvedSuggestion());
            try {
              Object result = plainReplay.replay(built.element());
              suggestedSelector = desc;
              capturedSuggestion = built.resolvedSuggestion().isPersistable() ? built.resolvedSuggestion() : null;
              usedCache = true;
              initialSelector = cached.initialSelector();
              needsReview = cached.needsReview();
              reviewNote = cached.reviewNote();
              healing = new Recovery("cache",
                  "Recovered using a previously-confirmed selector (" + desc + ") — no AI call needed.", desc, result);
              failureStage = null;
              attempts.add(HealAttempt.of("cache").succeeded(true).suggested(desc));
            } catch (Throwable cacheError) {
              attempts.add(HealAttempt.of("cache").succeeded(false).suggested(desc).error(normalizeError(cacheError)));
            }
          }
        }
      }

      snapshot = (attemptHealing && healing == null) ? captureSnapshot(ctx) : null;

      if (attemptHealing && healing == null && ctx != null) {
        HealProvider provider = ProviderFactory.getHealProvider();
        if (provider == null) {
          failureStage = "no_provider";
        } else {
          providerName = provider.getName();
          if (snapshot != null) {
            String scopedPhrase = c.description != null ? DurableLocator.stripGenericRoleSuffix(c.description) : null;
            String scopedSnapshot = scopedPhrase != null && !scopedPhrase.isEmpty()
                ? DurableLocator.extractScopedSnapshot(snapshot, scopedPhrase) : null;

            TextHealState st = new TextHealState();
            st.failureStage = failureStage;
            if (scopedSnapshot != null) {
              attemptTextHeal(provider, ctx, c, callArgs, snapshot, scopedSnapshot, true, timeoutMs, plainReplay, attempts, st);
            }
            if (st.healing == null) {
              attemptTextHeal(provider, ctx, c, callArgs, snapshot, snapshot, false, timeoutMs, plainReplay, attempts, st);
            }
            healing = st.healing;
            usage = st.usage;
            suggestedSelector = st.suggestedSelector;
            capturedSuggestion = st.capturedSuggestion;
            if (st.initialSelector != null) initialSelector = st.initialSelector;
            needsReview = st.needsReview;
            reviewNote = st.reviewNote;
            usedActionRecovery = usedActionRecovery || st.usedActionRecovery;
            failureStage = st.healing != null ? null : st.failureStage;
          }

          // --- vision ---
          if (healing == null && provider.supportsVision()) {
            VisionOutcome vo = tryVisionRecovery(provider, ctx, c, callArgs, timeoutMs, plainReplay);
            if (vo.usage != null) usage = TokenUsage.plus(usage, vo.usage);
            if (vo.healing != null) {
              healing = vo.healing;
              capturedSuggestion = vo.resolvedSuggestion;
              usedVision = vo.resolvedSuggestion == null;
              initialSelector = vo.initialSelector != null ? vo.initialSelector : initialSelector;
              needsReview = vo.needsReview;
              reviewNote = vo.reviewNote;
              failureStage = null;
              attempts.add(HealAttempt.of("vision").provider(provider.getName()).succeeded(true)
                  .suggested(vo.healing.suggestedSelector()));
            } else if (vo.stage != null) {
              usedVision = true;
              failureStage = vo.stage;
              attempts.add(HealAttempt.of("vision").provider(provider.getName()).succeeded(false)
                  .stage(vo.stage).error(FAILURE_STAGE_MESSAGES.get(vo.stage)));
            }
          }
        }
      }
    } finally {
      if (guardSet) {
        HEALING_IN_PROGRESS.set(Boolean.FALSE);
      }
      if (snapshot != null && ctx != null) {
        // leave data-tamash-ref only when a heal failed and we want the snapshot for the report
      }
    }

    // --- assemble ---
    String warning;
    if (healing != null) {
      warning = healing.warning();
    } else if ("replay_failed".equals(failureStage)) {
      warning = "Action \"" + report.action + "\" failed: " + reason + ". AI suggested \"" + suggestedSelector
          + "\", but that failed too.";
    } else if (failureStage != null) {
      warning = "Action \"" + report.action + "\" failed: " + reason + ". (" + FAILURE_STAGE_MESSAGES.get(failureStage) + ")";
    } else {
      warning = "Action \"" + report.action + "\" failed: " + reason + ".";
    }

    report.provider = healing != null ? healing.provider()
        : (providerName != null ? providerName : (actionabilityFailure ? "skipped" : (healingEnabled ? "none" : "disabled")));
    report.tokenUsage = usage;
    report.healed = healing != null;
    report.warning = warning;
    report.suggestedSelector = healing != null ? healing.suggestedSelector() : suggestedSelector;
    report.failureStage = failureStage;
    report.usedVision = usedVision;
    report.usedActionRecovery = usedActionRecovery;
    report.initialSelector = initialSelector;
    report.needsReview = needsReview;
    report.reviewNote = reviewNote;
    report.healedInAssertion = report.healed && c.inAssertion;
    report.ariaSnapshotForReport = report.healed ? null : snapshot;
    if (report.healedInAssertion && "warn".equals(assertionMode())) {
      noteAssertionHeal(c.testSelector);
    }

    REPORTS.add(report);

    // A locator polled inside a WebDriverWait fails once per poll. On a still-loading SPA a *valid*
    // locator isn't there for the first few seconds — those non-heals are noise, so stay quiet while
    // the failure count is low. Once it has failed well into the wait (~3s+) it's probably genuinely
    // broken: surface one line (and a failure-artifact dump) so it isn't a silent 20s timeout. The
    // negative-cache repeats ("recently declined for this DOM state") stay quiet throughout.
    boolean quiet = c.inWait && !report.healed
        && ("recently_declined".equals(report.failureStage) || HealCache.failCount(c.by) <= 6);
    boolean showAttempts = attempts.size() > 1 || (attempts.size() == 1 && !attempts.get(0).succeeded);
    if (!quiet) {
      (report.healed ? System.out : System.err).println(formatConsoleLine(report));
      if (showAttempts) {
        (report.healed ? System.out : System.err).println(formatAttemptsBlock(attempts));
      }
    }
    if (!report.healed && !quiet) {
      writeFailureArtifacts(report, snapshot);
    }
    logEligibleHeal(report, capturedSuggestion, usedActionRecovery, usedCache, c);

    // Feed the heal cache: a durable heal is reusable for the rest of the run; a decline is
    // remembered against the current DOM so a WebDriverWait's next poll doesn't re-pay for it.
    if (c.by != null && !"reentrant".equals(report.failureStage)) {
      if (report.healed && !usedCache && capturedSuggestion != null && capturedSuggestion.isPersistable()) {
        By healedBy = DurableLocator.toBy(capturedSuggestion);
        if (healedBy != null) {
          HealCache.recordPositive(c.by, pageKey, healedBy, report.suggestedSelector, capturedSuggestion);
        }
      } else if (!report.healed && attemptHealingWasTried(report.failureStage)) {
        HealCache.recordDeclined(c.by, domKey);
      }
    }

    Object result = healing != null ? healing.result() : null;
    return new HealResult(report, report.healed, result);
  }

  /** True for the outcomes where an actual provider attempt ran and came up empty — worth
   *  suppressing on the next identical-DOM poll. Not for infra states (disabled/no_provider). */
  private static boolean attemptHealingWasTried(String stage) {
    return stage != null && Set.of("ai_declined", "unbuildable_suggestion", "replay_failed",
        "vision_declined", "vision_unresolvable", "vision_replay_failed", "no_snapshot",
        "provider_error").contains(stage);
  }

  static String pageKeyOf(WebDriver driver) {
    try {
      String url = driver.getCurrentUrl();
      if (url == null) return "";
      int q = url.indexOf('?');
      int h = url.indexOf('#');
      int cut = url.length();
      if (q >= 0) cut = Math.min(cut, q);
      if (h >= 0) cut = Math.min(cut, h);
      return url.substring(0, cut);
    } catch (Exception e) {
      return "";
    }
  }

  static String domKeyOf(WebDriver driver) {
    try {
      Object v = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
          "return document.readyState + ':' + document.querySelectorAll('*').length + ':' + (document.title||'');");
      return v != null ? v.toString() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static WebElement safeFind(PageContext ctx, By by) {
    if (ctx == null || by == null) return null;
    ctx.enterFrame();
    try {
      return ctx.findOrNull(by);
    } finally {
      ctx.exitFrame();
    }
  }

  // ---- text heal (scoped-then-full) ----------------------------

  record Recovery(String provider, String warning, String suggestedSelector, Object result) {}

  static final class TextHealState {
    String failureStage;
    Recovery healing;
    TokenUsage usage;
    String suggestedSelector;
    AiSuggestion capturedSuggestion;
    String initialSelector;
    Boolean needsReview;
    String reviewNote;
    boolean usedActionRecovery;
  }

  private static void attemptTextHeal(HealProvider provider, PageContext ctx, HealContext c, Object[] callArgs,
                                      String fullSnapshot, String snapshotForPrompt, boolean scoped, double timeoutMs,
                                      Replayer plainReplay, List<HealAttempt> attempts, TextHealState st) {
    st.failureStage = "provider_error";
    ProviderResult result = provider.suggestSelector(
        new SuggestSelectorInput(c.action, c.description, snapshotForPrompt, timeoutMs,
            c.rawVariableName, c.originalByString, c.enclosingClass));
    if (result == null) {
      return;
    }
    st.usage = TokenUsage.plus(st.usage, result.getUsage());
    AiSuggestion suggestion = result.getSuggestion();
    if (suggestion == null || suggestion.isNone()) {
      st.failureStage = "ai_declined";
      return;
    }

    if ("ref".equals(suggestion.getStrategy())) {
      Built built = buildLocatorFromSuggestion(ctx, suggestion, c.action);
      if (built == null) {
        st.failureStage = "unbuildable_suggestion";
        return;
      }
      st.failureStage = "replay_failed";
      DerivedDurableLocator derived = deriveDurableLocator(ctx, built.element(), c.action,
          new TreeContext(fullSnapshot, List.of(suggestion.getRef()), suggestion.getNearbyRef(), suggestion.getNearbyText()),
          c.originalByString);
      WebElement primary = derived != null ? derived.element() : built.element();
      try {
        Object replayResult;
        boolean usedDerived = derived != null;
        try {
          replayResult = plainReplay.replay(primary);
        } catch (Throwable primaryError) {
          if (derived == null) throw primaryError;
          replayResult = plainReplay.replay(built.element());
          usedDerived = false;
        }
        if (usedDerived) {
          st.capturedSuggestion = derived.suggestion();
          st.initialSelector = derived.initialSelector();
          st.needsReview = derived.needsReview();
          st.reviewNote = derived.reviewNote();
          st.suggestedSelector = describeSuggestion(derived.suggestion());
        } else {
          st.capturedSuggestion = null;
          st.needsReview = true;
          st.reviewNote = "Healed via a one-shot element reference this run; no durable selector could be derived for future runs.";
          st.suggestedSelector = describeSuggestion(suggestion);
        }
        st.healing = new Recovery(provider.getName(),
            "Recovered using " + provider.getName() + " (" + st.suggestedSelector + ").", st.suggestedSelector, replayResult);
        st.failureStage = null;
        attempts.add(HealAttempt.of("ref").provider(provider.getName()).succeeded(true).suggested(st.suggestedSelector)
            .scoped(scoped).aiRefContext(suggestion.getRef(), suggestion.getNearbyRef(), suggestion.getNearbyText(),
                suggestion.getNearbyRole()));
      } catch (Throwable replayError) {
        attempts.add(HealAttempt.of("ref").provider(provider.getName()).succeeded(false)
            .suggested(st.suggestedSelector).stage("replay_failed").error(normalizeError(replayError)).scoped(scoped));
        maybeActionRecovery(provider, c.driver, built.element(), c.action, callArgs, replayError, timeoutMs,
            st.suggestedSelector, plainReplay, attempts, st);
      }
      return;
    }

    // structured (non-ref) suggestion
    Built built = buildLocatorFromSuggestion(ctx, suggestion, c.action);
    if (built == null) {
      st.failureStage = "unbuildable_suggestion";
      return;
    }
    st.suggestedSelector = describeSuggestion(built.resolvedSuggestion());
    st.capturedSuggestion = built.resolvedSuggestion().isPersistable() ? built.resolvedSuggestion() : null;
    st.failureStage = "replay_failed";
    try {
      Object replayResult = plainReplay.replay(built.element());
      st.healing = new Recovery(provider.getName(),
          "Recovered using " + provider.getName() + " (" + st.suggestedSelector + ").", st.suggestedSelector, replayResult);
      st.failureStage = null;
      attempts.add(HealAttempt.of("text").provider(provider.getName()).succeeded(true).suggested(st.suggestedSelector).scoped(scoped));
    } catch (Throwable replayError) {
      attempts.add(HealAttempt.of("text").provider(provider.getName()).succeeded(false)
          .suggested(st.suggestedSelector).stage("replay_failed").error(normalizeError(replayError)).scoped(scoped));
      maybeActionRecovery(provider, c.driver, built.element(), c.action, callArgs, replayError, timeoutMs,
          st.suggestedSelector, plainReplay, attempts, st);
    }
  }

  private static void maybeActionRecovery(HealProvider provider, WebDriver driver, WebElement element, String action,
                                          Object[] callArgs, Throwable replayError, double timeoutMs,
                                          String suggestedSelectorForReport, Replayer plainReplay,
                                          List<HealAttempt> attempts, TextHealState st) {
    if (!isActionRecoveryEnabled() || action == null) {
      st.healing = null;
      return;
    }
    ActionRecovery.Outcome ar = ActionRecovery.tryActionRecovery(provider, driver, element, action, callArgs,
        normalizeError(replayError), timeoutMs, plainReplay);
    st.usage = TokenUsage.plus(st.usage, ar.usage());
    st.usedActionRecovery = true;
    if (ar.healing() != null) {
      st.healing = new Recovery(ar.healing().provider(), ar.healing().warning(), suggestedSelectorForReport, ar.healing().result());
      st.failureStage = null;
      attempts.add(HealAttempt.of("action-recovery").provider(provider.getName()).succeeded(true).suggested(suggestedSelectorForReport));
    } else {
      st.healing = null;
      st.failureStage = ar.stage();
      attempts.add(HealAttempt.of("action-recovery").provider(provider.getName()).succeeded(false)
          .stage(ar.stage()).error(ar.stage() != null ? FAILURE_STAGE_MESSAGES.get(ar.stage()) : null));
    }
  }

  // ---- vision recovery --------------------------------------

  static final class VisionOutcome {
    Recovery healing;
    TokenUsage usage;
    String stage;
    AiSuggestion resolvedSuggestion;
    String initialSelector;
    Boolean needsReview;
    String reviewNote;
  }

  private static VisionOutcome tryVisionRecovery(HealProvider provider, PageContext ctx, HealContext c,
                                                 Object[] callArgs, double timeoutMs, Replayer plainReplay) {
    VisionOutcome out = new VisionOutcome();
    ctx.enterFrame();
    try {
      Vision.VisionCapture capture = Vision.captureElementForVision(ctx, timeoutMs);
      if (capture == null) {
        return out;
      }
      VisionProviderResult result = provider.suggestSelectorFromImage(
          new SuggestElementFromImageInput(c.action, c.description, capture.imageBase64(), timeoutMs));
      if (result == null) {
        out.stage = "vision_provider_error";
        return out;
      }
      out.usage = result.getUsage();
      VisionPoint point = result.getPoint();
      if (!point.isFound()) {
        out.stage = "vision_declined";
        return out;
      }
      Vision.ResolvedVisionPoint resolved = Vision.resolveElementAtVisionPoint(ctx, point, capture.box(),
          capture.scrollX(), capture.scrollY(), timeoutMs);
      if (resolved == null) {
        out.stage = "vision_unresolvable";
        return out;
      }
      WebElement el = ctx.findOrNull(Vision.visionTagSelector(resolved.id()));
      if (el == null) {
        out.stage = "vision_unresolvable";
        return out;
      }
      try {
        Object replayResult = plainReplay.replay(el);
        String freshSnapshot = DomSnapshot.capture(ctx.driver());
        DerivedDurableLocator derived = deriveDurableLocator(ctx, el, c.action,
            freshSnapshot != null ? new TreeContext(freshSnapshot, refCandidates(freshSnapshot, resolved), null, null) : null,
            c.originalByString);
        out.healing = new Recovery(provider.getName(),
            derived != null ? "Recovered using " + provider.getName() + " via visual match, upgraded to a durable locator."
                : "Recovered using " + provider.getName() + " via visual match.",
            derived != null ? describeSuggestion(derived.suggestion())
                : "visual match at (" + point.getX() + "," + point.getY() + ")",
            replayResult);
        out.resolvedSuggestion = derived != null ? derived.suggestion() : null;
        out.initialSelector = derived != null ? derived.initialSelector() : null;
        out.needsReview = derived != null ? derived.needsReview() : Boolean.TRUE;
        out.reviewNote = derived != null ? derived.reviewNote()
            : "Healed via a one-shot visual match this run; no durable selector could be derived for future runs.";
        return out;
      } catch (Throwable replayError) {
        out.stage = "vision_replay_failed";
        return out;
      } finally {
        Vision.cleanupVisionTag(ctx, resolved.id());
      }
    } finally {
      ctx.exitFrame();
    }
  }

  private static List<String> refCandidates(String snapshot, Vision.ResolvedVisionPoint p) {
    return DurableLocator.buildNearestRefCandidates(
        DurableLocator.parseAriaAiTree(snapshot), p.viewportX(), p.viewportY());
  }

  // ---- console formatting ----------------------------------

  private static String formatConsoleLine(SelfHealingReport r) {
    String outcome = r.healed ? "HEALED" : "NOT healed";
    List<String> meta = new ArrayList<>();
    meta.add("provider=" + r.provider);
    meta.add("vision=" + (r.usedVision ? "yes" : "no"));
    meta.add("actionRecovery=" + (r.usedActionRecovery ? "yes" : "no"));
    if (r.suggestedSelector != null) meta.add("suggested=\"" + r.suggestedSelector + "\"");
    if (r.failureStage != null) meta.add("stage=" + r.failureStage);
    if (r.tokenUsage != null) meta.add(r.tokenUsage.format());
    if (Boolean.TRUE.equals(r.needsReview)) meta.add("needsReview=yes");
    if (r.healedInAssertion) meta.add("assertion=yes");

    String tag = r.healedInAssertion ? "[self-healer][assertion] " : "[self-healer] ";
    String description = r.description != null ? " \"" + r.description + "\"" : "";
    String reasonFirstLine = r.reason.split("\n", 2)[0];
    String location = r.sourceLocation != null ? r.sourceLocation + " — " : "";
    return tag + location + r.kind + "." + r.action + description + " -> " + outcome
        + " [" + String.join(", ", meta) + "] — " + reasonFirstLine;
  }

  private static String formatAttemptsBlock(List<HealAttempt> attempts) {
    StringBuilder sb = new StringBuilder("  attempts:\n");
    for (int i = 0; i < attempts.size(); i++) {
      HealAttempt a = attempts.get(i);
      List<String> parts = new ArrayList<>();
      parts.add((i + 1) + ". " + a.method);
      parts.add(a.succeeded ? "OK" : "FAILED");
      if (a.provider != null) parts.add("provider=" + a.provider);
      if (a.suggestedSelector != null) parts.add("tried=\"" + a.suggestedSelector + "\"");
      if (a.stage != null) parts.add("stage=" + a.stage);
      if (a.error != null) parts.add("error: " + a.error.split("\n", 2)[0]);
      sb.append("    ").append(String.join("  ", parts));
      if (i < attempts.size() - 1) sb.append('\n');
    }
    return sb.toString();
  }

  private static void writeFailureArtifacts(SelfHealingReport report, String snapshot) {
    try {
      Path dir = Path.of("target", "tamash-selenium");
      Files.createDirectories(dir);
      String stamp = java.time.LocalDateTime.now().toString().replaceAll("[:.]", "-") + "-" + COUNTER.incrementAndGet();
      Files.writeString(dir.resolve("self-healing-" + report.action + "-" + stamp + ".json"),
          report.toJson().toString(2), StandardCharsets.UTF_8);
      if (snapshot != null) {
        Files.writeString(dir.resolve("self-healing-" + report.action + "-" + stamp + "-dom.txt"),
            snapshot, StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {
      // best-effort diagnostic output
    }
  }

  private static void logEligibleHeal(SelfHealingReport report, AiSuggestion suggestion, boolean usedActionRecovery,
                                      boolean usedCache, HealContext c) {
    if (!report.healed || report.sourceLocation == null
        || (suggestion == null && report.reviewNote == null) || usedActionRecovery) {
      return;
    }
    HealLog.SourceLocation loc = HealLog.parseSourceLocation(report.sourceLocation);
    if (loc == null) {
      return;
    }
    HealLog.Entry entry = new HealLog.Entry();
    entry.timestamp = java.time.Instant.now().toString();
    entry.file = loc.file();
    entry.line = loc.line();
    entry.action = report.action;
    entry.description = report.description;
    entry.suggestion = suggestion;
    // Store the rendered form too, so the jsonl is directly readable / re-verifiable by a human
    // without mentally running codegen. apply-heals still re-derives from `suggestion` (source of truth).
    if (suggestion != null) {
      entry.newLocator = DurableLocator.generateReplacementCall(suggestion);
      entry.newFindBy = DurableLocator.generateFindByAnnotation(suggestion);
    }
    entry.testSelector = c.testSelector;
    entry.testTitle = c.testTitle;
    entry.usedCache = usedCache;
    entry.initialSelector = report.initialSelector;
    entry.needsReview = report.needsReview;
    entry.reviewNote = report.reviewNote;
    HealLog.appendHealLogEntry(entry);
  }

  public static List<SelfHealingReport> getHealingReports() {
    return Collections.unmodifiableList(REPORTS);
  }
}
