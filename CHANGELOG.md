# Changelog

All notable changes to `tamash-selenium` are documented here. It versions independently of the
Playwright ports; this first release brings the self-healing engine to Selenium Java.

## [0.2.0] - 2026-09-01

First non-beta release. Functionally identical to `0.1.0-beta.4` — this bump drops the `-beta`
qualifier now that the engine, the seven providers, `apply-heals`, `doctor`, `init-skill`, and
both CI pipelines (library + sample) are verified end to end. Still `0.x`: the API (a tiny
surface — `SelfHealingDriver.wrap` plus the JUnit 5 / TestNG / Cucumber entry points) may still
evolve before `1.0`, which is gated on real-world mileage across more apps and teams.

Cumulative highlights since `0.1.0-beta.1`:

- **Self-healing for `findElement`** — DOM accessibility snapshot → provider → durable `By`
  derivation (verified against the live element) → retry. Never masks a genuinely-missing element.
- **Providers**: `tamash` (rule-based, zero-token, never guesses), `ollama` / `ollama-local`,
  `openai`, `anthropic`, `gemini`, `claude-subscription`, `copilot-subscription` — all verified.
- **Integrations**: `@UseTamashSelenium` (JUnit 5), `TamashSeleniumTestNgTest` (TestNG),
  the `com.vibetestq.qtpsudhakar.tamash.cucumber` glue; plain `SelfHealingDriver.wrap` for any
  runner. Page Object Model, `TamashPageFactory` / `@FindBy`, and keyword-driven (`Tamash.hint`).
- **Stale-element recovery** (always on) and **action recovery** (`scroll`/`force`/`wait`/`dispatch`,
  opt-in via `HEALER_ACTION_RECOVERY_ENABLED`).
- **`apply-heals`** — rewrites a healed locator into source (`By.x("…")`, `@FindBy(...)`, or a
  `By field = …` declaration) and writes a `verify-heals` script.
- **`doctor`** pre-flight checks and **`init-skill`** (installs the coding-agent skill into
  `.claude/skills/` + `.agents/skills/`).
- **HTML step report** (`TAMASH_REPORT`), two-tier heal cache, per-test attribution.
- Apache License 2.0; ~90 unit + ~127 browser tests; public GitHub Pages docs at
  <https://qtpsudhakarproducts.github.io/tamash-selenium/>.

---

## [0.1.0-beta.4] - 2026-09-01

Verification build-out, skill install, and open-sourcing.

### License

- **Relicensed to Apache License 2.0.** Previously a custom "free to use, not redistributable"
  license; now anyone may use, modify, and redistribute (including commercially), keeping the
  copyright/license notices (see `LICENSE` and `NOTICE`). The repository is now public.

### Added

- **`init-skill` CLI** — `mvn exec:java -Dexec.args="init-skill"` copies the coding-agent skill
  (`SKILL.md` + `references/`) into both `.claude/skills/tamash-selenium/` and
  `.agents/skills/tamash-selenium/`, the way Playwright's `install --skills` does. `--target`,
  `--user`, `--force`, `--dry-run`, `--dir`. A `.tamash-selenium-skill` version marker lets
  `doctor` flag a stale install.
- **`doctor` Skill check** — reports the install state of each location (current / behind / no
  marker / absent) and points at `init-skill`.
- **Test parity build-out** — new coverage matching the Playwright package's bar: `ReportRenderer`,
  `TamashRuleBasedProvider`, deeper `Prompt` parsing, `Http` retry (`HttpRetryTest`), a dedicated
  `HEALER_ENABLED=false` test, `<select>` / iframe / stale-replay-arg-forwarding / `getDurable` /
  report-cache-honesty browser tests, `AiHealingE2ETest` (verified vs openai/anthropic/gemini),
  `ActionRecoveryTest` (intercepted click recovered — verified vs all three providers) and
  `ActionRecoveryDisabledTest`. `SkillTest`. Unit ~43 → ~86; `mvn test -Pbrowser` ~123.
- **Library CI** — `.github/workflows/ci.yml`: `test` (unit + `-Pbrowser`), `package` + `doctor`
  smoke, gated `ai-providers` matrix.
- **`browser` Maven profile** — `mvn test` stays unit-only and fast; `mvn test -Pbrowser` runs the
  rule-based browser suite.

### Fixed

- **`apply-heals` — no verify script for a `By` field rewrite.** When a heal was landed on a
  `private By field = By.x("…")` declaration line (rather than an inline call or `@FindBy`), the
  affected-test list came back empty — the match keyed on the call-site line, not the declaration
  line — so no `verify-heals.sh` was written. Fixed; `ApplyHealsTest` now covers it.

### Changed

- **Skill layout** — the per-agent `adapters/` folder (`AGENTS.md`, `cursor-*.mdc`,
  `copilot-*.md`) is removed. One `SKILL.md` + `references/` serves every tool via the two
  standard skill directories; `init-skill` is the install path (the old `dependency:unpack`
  recipe still works for a manual copy).

### Docs

- `TESTING.md` rewritten to operating-manual depth; new `RELEASE-TESTING.md` (honest coverage
  matrix) and `TEST-PARITY-ROADMAP.md`.

---

## [0.1.0-beta.3] - 2026-09-01

Gemini reliability.

- **Gemini: thinking off by default.** Gemini 2.5+/3.x Flash think by default, so a ~5k-token
  selector request took 15–30s and blew the healer's per-call timeout. It now sends
  `reasoning_effort: low` (healing is structured extraction, not reasoning) — calls drop to ~2–3s.
  Set `GEMINI_THINKING=on` to keep the model default.
- **Per-call timeout 10s → 20s** (`TAMASH_ACTION_TIMEOUT_MS` still overrides).
- **Smarter HTTP retry.** A bare `429` is a per-minute quota — retrying with a 500 ms backoff just
  burned the timeout. Now: retry once, and only when the server named a short delay
  (`Retry-After` header or Gemini's `retryDelay` body field, ≤ 8s). 5xx gets one 1s retry; a
  timeout gets one immediate retry.

---

## [0.1.0-beta.2] - 2026-09-01

Compatibility, cost, and the `apply-heals` last mile.

### Changed

- **New coordinates.** Group id `io.github.qtpsudhakarproducts` → **`com.vibetestq.qtpsudhakar`**,
  base package `io.github.qtpsudhakarproducts.tamash` → **`com.vibetestq.qtpsudhakar.tamash`**.
  Update your dependency and re-run your IDE's optimize-imports (`SelfHealingDriver`,
  `@UseTamashSelenium`, `Tamash.hint`, `TamashPageFactory`, … all moved). `0.1.0-beta.1` at the old
  coordinates stays on Central but is not continued.
- **Runs on Java 21+** (was Java 25). No API past 21 is used.
- **`junit-jupiter-api` is now `<optional>`** — a JUnit 5 project already declares it; a TestNG-only
  project no longer gets it transitively (which made Surefire auto-select the JUnit Platform
  provider and silently run zero tests). JUnit 5 users: no change. TestNG users: drop the
  `<exclusion>` you may have added for beta.1.

### Removed

- **The vision fallback.** A full-page screenshot is ~35–40k tokens on GPT-4o (~10× a text heal),
  it was model-gated, and it could return a confident visual match for something that wasn't
  semantically the target. Text healing + durable-locator derivation covers the same ground far
  cheaper. Gone: `Vision*`, `HealProvider.supportsVision` / `suggestSelectorFromImage`, the vision
  prompts, the `vision=` console field and `used_vision` report field, the "Vision Fallback" doctor
  row. **Breaking** for anyone implementing `HealProvider` directly (two fewer methods).

### Added / fixed

- **`apply-heals` lands `By`-field Page Objects.** When the failing call site referenced a locator
  held in a field (`private final By loginBtn = By.id("old"); … driver.findElement(loginBtn)`),
  there was no `By.x(…)` literal on the recorded line to rewrite. The heal now also records the
  field's declaration line (`declarationLocation` in `heals.jsonl`) and `apply-heals` rewrites
  there.
- **`TAMASH_SOURCE_ROOTS` + sibling-module probing.** A multi-module build where the Page Objects
  live in one module and the running test in another now resolves element descriptions instead of
  falling back to the raw selector. `SourceLocations` probes `TAMASH_SOURCE_ROOTS` (comma-separated)
  and every `../*/src/{main,test}/java`.
- **Per-test heal attribution without an integration.** The auto-registered JUnit Platform listener
  sets `CurrentTest` and clears the per-test heal cache / hint around every test, so a bare
  `SelfHealingDriver.wrap(...)` gets `TamashHeals.forTest(...)`, per-test cache isolation, and
  grouped report rows — no `@UseTamashSelenium` needed.
- **Rule-based `tamash`: token-set matching.** When no accessible name is a substring of the
  description, match a node whose name/nearby text contains every significant word of the phrase in
  any order — so `"Username field"` matches a `Username` label.
- **Attribute stacking in durable derivation** — `input[type='password'][name='pwd']` before a
  structural XPath.
- **HTTP retry** — one backoff retry (honours `Retry-After`) on 429 / 5xx / timeout for the raw-HTTP
  providers.
- **`apply-heals` ignores a `By.x(…)` / `@FindBy` written inside a `//` comment.**
- **`TAMASH_BROWSER=safari`.**

---

## [0.1.0-beta.1] - 2026-08-30

First release. Shipped as a beta.

Verified end-to-end against a live app (OrangeHRM) — the text / `ref` / durable-derivation path
heals identically under all seven providers (`tamash` rule-based, `ollama`, `openai`, `anthropic`,
`gemini`, `claude-subscription`, `copilot-subscription`); the cache and the wait-context handling
are exercised the same way. `apply-heals` has a known limitation for `By`-field Page Objects
(fixed in beta.2).

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
  `mvn dependency:unpack -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.3 -Dmdep.unpack.includes="skills/**" -DoutputDirectory=.claude`.
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
