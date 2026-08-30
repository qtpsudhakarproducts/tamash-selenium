# Changelog

All notable changes to `tamash-selenium` are documented here. It versions independently of the
Playwright ports; this first release brings the self-healing engine to Selenium Java.

## [0.1.0-beta.1] - 2026-08-30

First release. Shipped as a beta.

Verified end-to-end against a live app (OrangeHRM) — the text / `ref` / durable-derivation path
heals identically under all seven providers (`tamash` rule-based, `ollama`, `openai`, `anthropic`,
`gemini`, `claude-subscription`, `copilot-subscription`); the cache and the wait-context handling
are exercised the same way. The vision fallback fires and is correctly flagged when text healing
declines; AI action-recovery is code-complete but lands a fix less often (model-dependent) and
stays opt-in. `apply-heals` has a known beta limitation for `By`-field Page Objects (see below).

Parity with the Playwright Java package's self-healing engine, adapted to Selenium's primitives.

### Added

- **Plug-and-play: `SelfHealingDriver.wrap(driver)` is the whole integration.** Every
  `findElement` through the wrapped driver heals — By-field Page Objects, `@FindBy` via plain
  `PageFactory.initElements`, `WebUtil` / keyword layers, and **inside a `WebDriverWait`**. No
  per-class or per-util changes.
- **`HealCache`** — a two-tier in-memory cache that makes healing inside a wait affordable: a
  selector healed once this run is reused instantly by every later failure (including the wait's
  next poll); a decline is remembered against the current DOM fingerprint so a genuine "not there
  yet" lets the wait poll normally. The framework integrations clear it per test.
- **Quiet SPA-load polling.** A locator first seen failing *inside* a `WebDriverWait`/`FluentWait`
  is given a few polls before the first snapshot/provider call — on a still-loading single-page app
  a valid locator resolves within that window. While the failure count stays low (~3s of polling)
  no `NOT healed` line or failure-artifact dump is emitted; once a locator keeps failing well into
  the wait it is surfaced (one line) so a genuinely broken locator isn't a silent 20s timeout. The
  negative-cache repeats stay quiet throughout, and successful heals inside a wait always print.
  Applies to both the plain `driver.findElement` path and PageFactory `@FindBy` fields resolved
  inside `ExpectedConditions.visibilityOf(...)` (`SourceLocations.calledFromWait` is shared by
  `HealingInvocationHandler` and `LazyHealingElementHandler`).
- **Rule-based `tamash` is the default provider** when `HEALER_PROVIDER` is unset — healing works
  with zero configuration (no key, no network, no tokens; never guesses).
- **Wrapping pins implicit wait to 0** (`TAMASH_KEEP_IMPLICIT_WAIT=true` to opt out) — a broken
  find surfaces immediately for the healer, and mixing implicit + explicit waits is a Selenium
  anti-pattern anyway.
- **`@UseTamashSelenium` (JUnit 5), `TamashSeleniumTestNgTest` (TestNG), and a Cucumber glue
  package** — *optional* conveniences: they manage the driver lifecycle and attribute each heal to
  its test / render the HTML step report. `org.testng:testng` and `io.cucumber:cucumber-java` are
  `provided`.
- **`TamashPageFactory` / `TamashFieldDecorator`** — *optional*: `@FindBy` heals through the
  wrapped driver already; these make the healer's description the **field name** rather than the
  raw selector.
- **Call-site name resolution through indirection** — the healer's description / AI hint is
  resolved across up to 3 consumer stack frames: a same-line `Type x = driver.findElement(...)`
  assignment; a bare reference (`findElement(loginButton)`, `loginButton.click()`,
  `wait.until(<cond>(by))`); a locator argument passed into a helper
  (`WebUtil.click(driver, loginButton)` — the name lives at the util's caller, not inside the
  util); or an enum-constant / accessor token (`LoginPage.txtUserName.enterText(...)`,
  `loginPage.usernameField().sendKeys(...)`). The heal is attributed to that frame, so
  `.tamash-selenium/heals.jsonl` points at the Page Object, not the shared `WebUtil`.
- **`Tamash.hint(name)`** — the explicit description hook (an `AutoCloseable` scope) for
  keyword-driven suites, opaque locator names, or heavy wrapper indirection where no usable name
  reaches the call site. Takes precedence over the automatic decode; cleared per test by the
  framework integrations.
- **Assertion-context handling** — a broken locator inside an assertion heals (a locator is
  plumbing; the assertion is the intent) and is logged distinctly (`[self-healer][assertion]`).
  `HEALER_ASSERTIONS=warn` heals but flags each affected test and prints an end-of-run summary;
  `HEALER_ASSERTIONS=strict` fails natively inside assertions. An **"assert absent"**
  (`assertThrows(NoSuchElementException.class, …)`, `invisibilityOfElementLocated`, `stalenessOf`,
  `numberOfElementsToBeLessThan`, `*absent*` / `*notPresent*` helper names) is **never** healed —
  a heal there would defeat the assertion. `findElements(by).isEmpty()` remains the un-healed
  absence check.
- **Proxy-based `WebDriver` / `WebElement` bindings** — `findElement` / `findElements` re-wrap
  their results; the interactive `WebElement` methods are intercepted for healing. The proxy
  exposes every interface the concrete driver/element implements (`JavascriptExecutor`,
  `TakesScreenshot`, `Interactive`, `WrapsDriver`, …).
- **`@FindBy` / PageFactory support** — heals through the wrapped driver with plain
  `PageFactory.initElements(driver, page)`. `TamashPageFactory.initElements(driver, page)` (a
  drop-in) additionally makes the healer's description the **field name** and heals a nested
  `container.findElement(childBy)` on a `@FindBy` container. Resolution stays lazy (via the wrapped
  `ElementLocator`, so `@CacheLookup` / `AjaxElementLocatorFactory` still apply). `apply-heals`
  rewrites the `@FindBy(...)` annotation in place. `@FindBy List<WebElement>` fields fall through
  to Selenium's default.
- **`WrapsElement` on the wrapped element** — a healed element now works with `Actions`,
  `new Select(...)`, and any Selenium code that unwraps via `WrapsElement`, instead of hitting a
  `JsonException` from Selenium's internal cast to `RemoteWebElement`.
- **Verified against a pattern gauntlet** — fluent Page Objects, `Select`, `Actions`,
  stale-element re-heal, `@FindBy` + `AjaxElementLocatorFactory`, nested container finds,
  `@ParameterizedTest` (cache), multiple broken locators per test, `FluentWait`,
  wait-for-presence-then-act. `findElements` (plural) is never healed.
- **Eager-find healing model** — a broken `driver.findElement(by)` is healed at find time
  (including inside a `WebDriverWait` — see `HealCache` above); a `StaleElementReferenceException`
  re-finds with the original locator first, then heals; interactability errors run action recovery.
- **`DomSnapshot`** — a JS-injected accessibility tree (`- role "name" [ref=eN] [box=…]`) that
  replaces Playwright's `ariaSnapshot(mode:AI)`. Every element is stamped `data-tamash-ref` so an
  AI-picked `[ref=eN]` resolves via `By.cssSelector("[data-tamash-ref='eN']")`.
- **`DurableLocator`** — the tree parser, `findAdjacentBranchPath`, `findSiblingAnchorTexts`,
  `extractScopedSnapshot`, `findRuleBasedMatch`, and `buildNearestRefCandidates` carried over from
  the Playwright port; plus `deriveSuggestionFromElement` (a stable `By` from a resolved element:
  real `id` → `name` → `data-testid` / `aria-label` CSS → link text → class combo → structural
  near/adjacent XPath), `toBy` (`AiSuggestion` → `By`), and `generateReplacementCall`
  (`AiSuggestion` → `By.id(…)` / `By.name(…)` / `By.cssSelector(…)` / `By.xpath(…)` source).
- **Eight providers** — `ollama`, `ollama-local`, `openai`, `anthropic`, `gemini`,
  `claude-subscription`, `copilot-subscription`, and the rule-based `tamash` (no key, no network,
  no tokens). `anthropic` / `claude-subscription` use the official `com.anthropic:anthropic-java`
  SDK; `copilot-subscription` the official (optional) `com.github:copilot-sdk-java`.
- **Selenium-native fallback strategy set** — the AI returns `ref` (primary), or `id` / `name` /
  `css` / `xpath` / `text` / `near` / `none`.
- **Richer finder prompt** — the AI healer is also given the raw (undecoded) locator variable /
  `@FindBy` field name, the broken selector string, and the enclosing Page Object / test class
  name, so it can expand a terse name (`txtUserName`) itself and read intent from the old
  selector. No extra call — the deterministic name decoder still produces the primary hint and
  the rule-based (`tamash`) input.
- **Vision fallback** — screenshot via `TakesScreenshot`, mapped to viewport pixels and resolved
  with `document.elementFromPoint`.
- **Action recovery** — `scroll` (JS `scrollIntoView`), `force` (JS click / `Actions`), `wait`,
  `dispatch` (JS `dispatchEvent`). Opt-in via `HEALER_ACTION_RECOVERY_ENABLED=true`.
- **`heals.jsonl` cache + history archival** under `.tamash-selenium/`. Each entry carries the
  structured `suggestion` (source of truth) **and** its rendered `newLocator` /
  `newFindBy` strings (`By.name("firstName")` / `@FindBy(name = "firstName")`) so the log is
  directly readable and re-verifiable without running codegen.
- **`doctor` CLI** — provider connectivity (live call), implicit-wait report, vision-capability
  label, brittle-locator naming, inline-locator flagging.
- **Agent skill** — `skills/tamash-selenium/` (`SKILL.md` + `references/onboarding.md` +
  `references/heal.md` + Cursor / Copilot / `AGENTS.md` adapters), shipped inside the JAR under
  `skills/`. Drives the local onboard → run → review → apply → verify → land loop for Claude Code,
  Kiro, Cursor, Copilot, and any `AGENTS.md`-reading agent. Extract with
  `mvn dependency:unpack -Dartifact=io.github.qtpsudhakarproducts:tamash-selenium:0.1.0-beta.1 -Dmdep.unpack.includes="skills/**" -DoutputDirectory=.claude`.
- **`apply-heals` CLI** — rewrites a `By.xxx("…")` literal or `@FindBy(...)` annotation on the
  recorded line to the confirmed selector, writes Markdown + JSON reports, and generates a
  `verify-heals.sh` / `.cmd`. *Beta limitation:* a locator kept in a separate `private final By`
  field (call site references only the field) has no literal on the recorded line — that heal is
  reported *skipped*; apply it by hand from `heals.jsonl`'s `newLocator` / `newFindBy`.
- **HTML step report** (`TAMASH_REPORT=<path>`) — auto-registered JUnit Platform / TestNG
  listener; zero overhead when unset.
- **iframe healing** — a `frameChain` is threaded through so the healer switches into the frame
  before capturing / finding and back to `defaultContent()` after.
- **`TAMASH_BROWSER`** (chrome | firefox | edge), **`TAMASH_REUSE_DRIVER`**, **`HEADLESS`**,
  **`TAMASH_ACTION_TIMEOUT_MS`** (bounds the healer's own snapshot / JS calls).

### Selenium-specific divergences from the Playwright port

- **No `assertThat` shim** — Selenium has no assertion library, and a wrapped `WebElement`
  satisfies any JUnit / AssertJ / Hamcrest / TestNG assertion directly.
- **Eager finds** — Selenium's `findElement` resolves eagerly; the healer runs on the throw. The
  `HealCache` handles the repeated polling a `WebDriverWait` does.
- **No `Locator.normalize()`** — a durable `By` is derived by inspecting the resolved element
  (`deriveSuggestionFromElement`).
- **Implicit wait pinned to 0** by the lifecycle (the equivalent of the Playwright port capping
  the per-action timeout).
