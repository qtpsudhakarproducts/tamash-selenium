# Test Parity Roadmap

Bring `tamash-selenium`'s verification up to the level `tamash-playwright` (the TypeScript
reference at `D:\QtpSudhakarOrg\tamash-playwright`) holds itself to: every strategy, fallback,
CLI behaviour, and framework integration covered by *either* a fast deterministic unit test *or*
a real end-to-end run against a live browser (and, where it matters, a real AI provider) — and an
honest, current record of which is which.

This is a living document. Update the status columns as phases land. Do not delete a completed
phase's notes — the point is the trail.

## The bar we are matching

`tamash-playwright` has, as of 2026-09:

- **~165 unit tests across 11 files** — `applyHeals` (all 9 strategies + chain consumption),
  `healLog`, `extractVariableName`, `decodeVariableName` (20+ cases), `durableLocator` (tree parser
  + widening ladders against *real captured* snapshot fixtures), `tamashRuleBasedProvider` (19
  tests — every decline path), `resolveCallerLocation` (incl. the `file://` ESM regression),
  `prompt` (26 tests — every parse shape), `providerDiagnostics` (13 cases), `skill` (real
  installs into a temp dir), `stripAnsi`.
- **33 e2e specs** — real browser + real AI. Per-strategy resolution, ref pipeline, `getDurable`
  (incl. concurrency), replay-argument-forwarding, `<select>` healing (5 specs), action recovery,
  vision, popup/new-tab, `healing-disabled` master switch, `apply-heals` round-trip, report/cache
  honesty, canonical live-site examples.
- **`TESTING.md`** — a ~500-line operating manual (verify-for-real rule, deterministic
  text-path-failure recipe, fixture traps, module-system-sensitivity, the 9-step e2e checklist).
- **`RELEASE-TESTING.md`** — a feature × {tested locally, tested in CI} matrix with an honest
  legend (verified / manual-only / not-re-checked / not-covered) plus per-release notes recording
  real mistakes testing caught.
- **Sample-repo CI** — two consumer repos, each running `test-tamash`, `test-ollama`, a
  subscription provider, **plus a dedicated `apply-heals` job that opens a real PR**.
- **Skill** — `SKILL.md` + `references/` only, installed into `.claude/skills/` **and**
  `.agents/skills/` by `init-skill`, with a version marker `doctor` checks. No `adapters/` folder.

## Where tamash-selenium is now (baseline, 2026-09-01)

- **~43 unit tests / 9 files** — `SourceLocationsTest` (9), `DurableLocatorTest` (12),
  `HealCacheTest` (5), `HealLogTest` (5), `ApplyHealsTest` (5), `PromptTest` (4),
  `FrameworkIntegrationTest` (2), `TestNgSmoke` (1).
- **~21 browser tests / 8 files** — `PatternsHealingTest` (11-scenario gauntlet, rule-based),
  `AssertionHealingTest` (3), `SampleTest`, `TamashRuleBasedHealingTest`, `TamashHintTest` (2),
  `PageFactoryHealingTest`, `WaitHealingTest`, `WebUtilHealingTest`. All rule-based (`tamash`),
  mostly `data:text/html` pages.
- No real-AI e2e in the library. No `<select>` / iframe / action-recovery / replay-arg /
  `getDurable` / dedicated `healing-disabled` tests. No `ReportRendererTest`,
  `TamashRuleBasedProviderTest`, deep `PromptTest`, `HttpRetryTest`, optional-dependency-absent
  test. No `RELEASE-TESTING.md`. No library CI. Sample CI has no `apply-heals` job, no ollama, no
  subscription providers.
- Skill still uses the old `adapters/` layout; no `init-skill`, no `doctor` Skill check.

---

## Phases

Order is roughly dependency order, but phases 1–4 are independent and can be done in any order.
Each phase ends with `mvn test` green and its row(s) in `RELEASE-TESTING.md` updated to reflect
what was *actually run*, not what was written.

### Phase 0 — Infrastructure & honesty layer

| Item | Status |
|---|---|
| `RELEASE-TESTING.md` — feature × {local, CI} matrix, honest legend, seeded with today's real state | ✅ 2026-09-01 |
| `TESTING.md` — operating-manual depth: verify-for-real rule, `MAX_SNAPSHOT_CHARS` note, `.env` backup discipline, heal-log-is-a-cache trap, nested-assignment name-steal trap, the 9-step e2e checklist | ✅ 2026-09-01 |
| `.github/workflows/ci.yml` in the **library** repo — `test` job (`mvn test` then `mvn test -Pbrowser`, headless Chrome), `package` + `doctor` smoke, `ai-providers` matrix gated on secrets | ✅ 2026-09-01 (not yet pushed / run) |
| `pom.xml` — new `browser` profile un-excludes the rule-based browser suite; `mvn test` stays unit-only | ✅ 2026-09-01 |
| This roadmap committed | ☐ needs the user (commit only when asked) |

### Phase 1 — Missing deterministic unit tests (no browser, no AI)

| Test | Mirrors | Status |
|---|---|---|
| `report/ReportRendererTest` — synthetic report → HTML: summary stats, per-test rows, heal note, token cost, escaped step text, empty-steps placeholder, unrecovered-failure DOM-snapshot block | pw-java `ReportRendererTest` | ✅ 2026-09-01 (5 tests) |
| `healer/providers/TamashRuleBasedProviderTest` — `parseDescriptionForMatch` (phrase + type hint, generic-suffix strip, empty guards), `suggestSelector` decline paths, `suggestActionTactic` always `NONE` | pw `tamashRuleBasedProvider.test.js` (19) | ✅ 2026-09-01 (9 tests) |
| `healer/providers/PromptTest` — **deepen from 4 → ~20**: `parseSuggestion` for every strategy (`id`/`name`/`css`/`xpath`/`ref`/`text`/`near`/`none`), markdown-fenced JSON, missing-required-field rejection, `parseActionTactic`, usage extraction, system-prompt wording (Selenium not Playwright) | pw `prompt.test.js` (26) | ✅ 2026-09-01 (15 tests) |
| `bindings/SourceLocationsTest` — add dedicated `decodeVariableName` cases: `txtEmployeeId` → phrase + hint, `submitBtn`, snake_case, meaningless-word guard (`txt`/`button` alone), prefix false-positives (`imgur` ≠ `img`) | pw `decodeVariableName.test.js` (10) | ✅ already covered (5 cases in `SourceLocationsTest`) |
| `healer/providers/HttpRetryTest` — in-JVM `com.sun.net.httpserver` stub: `Retry-After` header parse, Gemini `retryDelay` body regex, 8s cap (no retry beyond), single-retry-then-give-up, timeout → one immediate retry, 5xx → 1s | new (beta.3 code, no coverage) | ✅ 2026-09-01 (7 tests) |
| `OptionalDependencyAbsentTest` — isolated `URLClassLoader` with JUnit / TestNG / Cucumber jars stripped: core `SelfHealingDriver.wrap` runs; JUnit integration links without TestNG+Cucumber; TestNG without JUnit+Cucumber; Cucumber without JUnit+TestNG | pw CJS/ESM regression | ✅ 2026-09-01 (4 tests) |
| `healer/HealingScenariosTest` (offline, `tamash` provider, `data:` pages) — port pw-java's: `healing-disabled` fails like vanilla, assertion/`waitFor`-absent never AI-healed (`tokenUsage == null`), scenario mirrors | pw-java `HealingScenariosTest` | ☐ (also Phase 2) |

### Phase 2 — Library real-browser e2e (rule-based `tamash`, deterministic)

| Test | Mirrors | Status |
|---|---|---|
| `SelectHealingTest` — broken `By` for a `<select>`, `Select` wrapper works on the healed element, multi-select | pw `select-*-repro` (5) | ✅ 2026-09-01 (2). Selenium's `Select` surface is smaller — noted in the file. |
| `IframeHealingTest` — broken locator inside `<iframe srcdoc=…>`; `switchTo().frame` is stateful so the snapshot runs in-frame; `defaultContent()` after | pw `iframe-ref-repro` | ✅ 2026-09-01 (1) |
| `HealingDisabledTest` — dedicated, asserted: `HEALER_ENABLED=false` → broken `findElement` throws like vanilla; working locator unaffected; no token usage | pw `healing-disabled.e2e` | ✅ 2026-09-01 (2) |
| `ReplayArgForwardingTest` — stale-then-replayed `sendKeys("A","B","C")` forwards every arg; replayed `getDomAttribute("name")` forwards the arg | pw `replay-second-arg` / `replay-multi-option` | ✅ 2026-09-01 (2) |
| `GetDurableTest` — `getDurable(By)` → durable `By`; stable across repeated calls (no static-cache drift); throws on non-resolving locator. (No `getDurable(WebElement)` overload exists; Selenium can't safely drive one `WebDriver` from N threads, so "concurrency" is repeated-call stability.) | pw `get-durable-*` (4) | ✅ 2026-09-01 (3) |
| `ReportCacheHonestyTest` — every reported/cached selector independently resolves to the intended element. The rare "derived → threw → raw ref succeeded" TOCTOU trigger is a **documented gap** (pw couldn't repro it live either). | pw `ref-report-accuracy-repro` | ✅ 2026-09-01 (1) |
| `WaitHealingTest#waitOnGenuinelyAbsentElementTimesOutAndDoesNotHeal` — a never-appearing element times out like vanilla, not "healed" | pw-java `HealingScenariosTest` | ✅ 2026-09-01 |
| `ApplyHealsRoundtripTest` — full heal → `apply-heals --yes` → `verify-heals` with healing off | pw `apply-heals-ref-roundtrip` | ☐ deferred to Phase 3 sample-CI `apply-heals` job (pw proves it the same way) |

### Phase 3 — Real-AI e2e (multi-provider)

| Item | Status |
|---|---|
| `AiHealingE2ETest` — three broken locators (wrong `name` / `#id` / button text) healed via a live provider; asserts each heal came from that provider with a `By.…` suggestion; `assumeTrue`s a real provider (skipped under plain `mvn test`); deletes `heals.jsonl` in `@BeforeAll` to defeat the cross-run cache | ✅ 2026-09-01 — verified locally against **openai** (gpt-4o-mini), **anthropic** (claude-haiku-4-5), **gemini** (gemini-flash-lite-latest) |
| Library CI `ai-providers` job — matrix openai/anthropic/gemini, runs `AiHealingE2ETest,ActionRecoveryTest`, gated on `secrets.*` | ✅ 2026-09-01 (in `.github/workflows/ci.yml`, not yet run) |
| Sample CI — `ollama` leg | ✅ 2026-09-01 (gated on `OLLAMA_API_KEY`) |
| Sample CI — **`apply-heals` job**: heal → `apply-heals --yes` → `verify-heals` with healing off → upload the report as an artifact (no PR — this sample's only broken locators ARE the demo) | ✅ 2026-09-01 |
| **Bug found + fixed**: `apply-heals`'s affected-test list was empty for a `By field` declaration-line rewrite (matched the call-site line, not the declaration line) → no `verify-heals.sh` generated. Fixed in `ApplyHeals.planFixes`; covered by `ApplyHealsTest`. | ✅ 2026-09-01 |
| `SelfHealingDemoTest` (junit5 + testng) — guard the heal-metadata assertions behind `Healer.isHealingEnabled()` so `verify-heals` (healing off, locators rewritten) passes on the page-state assertions | ✅ 2026-09-01 |
| Full round-trip verified locally against the sample (heal via anthropic → apply-heals → verify-heals passes) | ✅ 2026-09-01 |
| Re-verify `claude-subscription`, `copilot-subscription`, `ollama` locally | ✅ 2026-09-01 — all pass `AiHealingE2ETest`; `ollama` + `claude-subscription` also pass `ActionRecoveryTest` |
| Actually run the library CI + sample CI | ✅ 2026-09-01 — both green on `0.1.0-beta.4`. Library run `33528061152`; sample run on beta.4 confirmed the `apply-heals` job generates a real `verify-heals.sh` (`Tests to re-verify: …SelfHealingDemoTest`) and it passes with healing off. |

### Phase 4 — Action recovery

| Item | Status |
|---|---|
| `ActionRecoveryTest` — `HEALER_ACTION_RECOVERY_ENABLED=true` + an AI provider + a click-intercepted overlay fixture; asserts `usedActionRecovery`, records whether it recovers | ✅ 2026-09-01 — **recovered cleanly against openai, anthropic AND gemini** (Selenium's FORCE tactic = JS `.click()`, which genuinely fires the button's onclick past the overlay; better than pw's flaky experience) |
| `ActionRecoveryDisabledTest` — the default: with the flag off, an intercepted click surfaces as `ElementClickInterceptedException`, never swallowed (rule-based, no AI) | ✅ 2026-09-01 (1) |
| `StaleElementReferenceException` cheap-re-find path | ✅ already covered by `PatternsHealingTest#staleElementReHeal` |
| `RELEASE-TESTING.md` action-recovery row updated | ✅ 2026-09-01 |

### Phase 5 — Skill migration to the playwright model ✅ 2026-09-01

| Item | Status |
|---|---|
| `cli/Skill.java` — `TARGETS` (`.claude/skills/` + `.agents/skills/`), `installSkill`, `skillState` (CURRENT/OUTDATED/UNMANAGED/ABSENT), `.tamash-selenium-skill` marker, `legacyInstallArtifacts` | ✅ |
| `cli/Main.java` — `init-skill [--target claude\|agents] [--user] [--force] [--dry-run] [--dir <path>]` | ✅ |
| `cli/Doctor.java` — Skill check row | ✅ |
| Delete `skills/tamash-selenium/adapters/` | ✅ (verified absent from a clean-built jar) |
| `cli/SkillTest` — real installs into a temp dir; `adapters/` **not** copied; idempotency, version bump, `--force`, `--dry-run`, state transitions, legacy detection | ✅ (11 tests) |
| `pom.xml` — `skills/**` still bundled; new filtered `build.properties` so `getPackageVersion()` works from `target/classes` too | ✅ |
| README / USAGE / SKILL.md — `init-skill` replaces the unpack recipe | ✅ |
| Verified end-to-end: `init-skill` (both targets + idempotent re-run), `doctor` Skill row (current / not-installed) | ✅ |

### Phase 6 — Finalise

| Item | Status |
|---|---|
| `RELEASE-TESTING.md` fully populated — every feature row reflects a real run | ✅ 2026-09-01 |
| `TESTING.md` operating manual complete | ✅ 2026-09-01 |
| `CHANGELOG.md` — entry for the verification build-out + the `apply-heals` fix | ✅ 2026-09-01 |
| This roadmap — every box checked or explicitly deferred with a reason | ✅ 2026-09-01 |
| Publish the build (`apply-heals` fix + `init-skill`) so the sample CI's `apply-heals` job resolves it | ☐ needs the user (outward-facing) |
| Run the library CI + sample CI for real once published | ☐ needs the user |
| Cut `0.1.0` proper (stop the beta churn) | ☐ needs the user |

### Explicitly out of scope

- **Non-Chrome browsers** (Firefox / Edge / Safari) — user decision 2026-09-01. The lifecycle
  supports `TAMASH_BROWSER=firefox|edge|safari`; those branches stay hand-tested only.
- **Vision** — removed from the package (`0.1.0-beta.2`).
- **`peter-evans/create-pull-request` PR from the sample's `apply-heals` job** — the sample's only
  broken locators are the demo itself, so there is nothing to land; the job proves the round-trip
  and uploads the report as an artifact instead.

---

## Selenium-specific notes (things the TS manual doesn't cover)

- **`MAX_SNAPSHOT_CHARS` is 16000** (`healer/providers/Prompt.java:18`), not TS's 8000. Filler for
  a deterministic "AI can't see it" fixture must exceed that, placed *before* the target in DOM
  order.
- **`findElement` resolves eagerly** — a broken locator throws `NoSuchElementException` before any
  action, so the direct-find heal path is the common one; the action name is `null` for role
  inference in that case. Inside a `WebDriverWait`/`FluentWait`, the first ~3 polls are deferred
  and the rest suppressed (`SourceLocations.calledFromWait`).
- **Implicit wait is set to 0** by the lifecycle so a broken find surfaces immediately.
- **No `assertThat`** — a wrapped `WebElement` satisfies any JUnit/AssertJ/Hamcrest assertion by
  implementing the same interface. Assertion healing is gated by `HEALER_ASSERTIONS=heal|warn|strict`.
- **`ReportRenderer` is package-private** (`report/ReportRenderer`) with `static String
  render(List<JSONObject>)` — the test must sit in `com.vibetestq.qtpsudhakar.tamash.report`.
- **Provider is read once and cached** (`ProviderFactory`) — set `HEALER_PROVIDER` before the
  process starts, or call `ProviderFactory.resetCache()` after `System.setProperty`.
- **Heal cache defeats its own test** — `heals.jsonl` + the in-memory positive cache are keyed by
  `locator + pageKey`, not provider. Clear with `HealCache.clear()` in `@BeforeEach` (the
  auto-registered `TamashReportListener` already does this per test, but standalone/`data:` tests
  may not go through it).
- **Single-module only** — Page Object description recovery reads the source file relative to the
  test's working dir. Multi-module breaks it (mitigated by `TAMASH_SOURCE_ROOTS` + `../*/src`
  probing, but keep e2e fixtures single-module).
