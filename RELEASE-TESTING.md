# Release testing record

The trust layer: an honest, current account of what has actually been verified for every feature
users depend on — not a claim that it works, but evidence for how we know. A status here reflects
something genuinely run and observed. Where something hasn't been checked, or was checked before
but not re-verified recently, that is stated plainly rather than rounded up to look complete. A
gap shown here is more useful than a gap hidden.

**Status legend**: ✅ Verified (automated test exists and passes) · 🔎 Verified manually, no
permanent automated test yet · ⚠️ Verified previously, not re-checked this release · ❌ Not
currently covered · N/A not applicable.

See [`TEST-PARITY-ROADMAP.md`](TEST-PARITY-ROADMAP.md) for the plan to close the ❌ rows and
[`TESTING.md`](TESTING.md) for how to run each layer.

## Feature coverage

| Feature | Tested locally | Tested in CI |
|---|---|---|
| Core `findElement` healing (broken `By` → snapshot → provider → durable `By` → retry) | ✅ `PatternsHealingTest` (11), `TamashRuleBasedHealingTest` (rule-based); `AiHealingE2ETest` (openai/anthropic/gemini, verified locally 2026-09-01) | ✅ sample repo + library CI `ai-providers` job (not yet run) |
| Master switch `HEALER_ENABLED=false` | ✅ `HealingDisabledTest` (2 — broken throws like vanilla + no token usage; working locator unaffected) | 🔎 library CI `browser` job includes it |
| Stale-element recovery (cheap re-find + full heal) | ✅ `PatternsHealingTest#staleElementReHeal` | 🔎 exercised indirectly |
| Wait-context suppression (`WebDriverWait`/`FluentWait` — defer first polls, quiet the rest) | ✅ `PatternsHealingTest#fluentWait` / `#waitForPresenceThenAct`, `WaitHealingTest` | 🔎 exercised on the sample's SPA navigations |
| `@FindBy` / PageFactory healing (`TamashPageFactory`, `TamashFieldDecorator`, `AjaxElementLocatorFactory`) | ✅ `PatternsHealingTest#findByWithAjaxLocatorFactory` / `#nestedFindOnContainer`, `PageFactoryHealingTest` | ✅ sample `AddEmployeeFactoryTest` (JUnit5 + TestNG) |
| Structural / durable-`By` derivation (id→name→testid→aria→placeholder→link→class→stacked-attrs→XPath) | ✅ `DurableLocatorTest` (12) against captured snapshot fixtures | 🔎 every AI heal in sample CI exercises it |
| Token-set / reordered-phrase matching (`findRuleBasedMatch` fallback) | ✅ `DurableLocatorTest#findRuleBasedMatch_tokenSetFallback` | ❌ |
| Attribute stacking (`stackedAttributeCss`, ≥2 of name/type/role/placeholder/aria-label/title/data-name) | ✅ `DurableLocatorTest` | ❌ |
| `tamash` rule-based provider (whole provider — parse, decline paths, always-decline tactic) | ✅ `TamashRuleBasedProviderTest` (9) + `DurableLocatorTest` | ✅ sample `test` job's default provider |
| Prompt building + response parsing (every strategy, markdown-fenced JSON, missing-field reject, action tactic, usage) | ✅ `PromptTest` (15) | N/A |
| `<select>` / dropdown healing | ✅ `SelectHealingTest` (2 — healed select + multi-select via `Select`) | 🔎 library CI `browser` job |
| iframe healing (stateful `switchTo().frame`) | ✅ `IframeHealingTest` (1 — heal in `srcdoc` frame, return to default content) | 🔎 library CI `browser` job |
| Replay reuses the entire original call (multi-arg `sendKeys`) | ✅ `ReplayArgForwardingTest` (2) | 🔎 library CI `browser` job |
| `getDurable(By)` public API | ✅ `GetDurableTest` (3 — brittle XPath → durable `By`, repeated-call stability, throws on no-resolve). No `getDurable(WebElement)` overload exists. | 🔎 library CI `browser` job |
| Report / cache honesty (only the locator that actually acted is reported/cached) | ✅ `ReportCacheHonestyTest` (1 — every reported selector independently resolves). TOCTOU trigger a documented gap. | 🔎 library CI `browser` job |
| Action recovery (scroll/force/wait/dispatch) — opt-in `HEALER_ACTION_RECOVERY_ENABLED=true` | ✅ `ActionRecoveryTest` (intercepted click → recovered, verified vs openai/anthropic/gemini 2026-09-01); `ActionRecoveryDisabledTest` (flag off → real exception, not swallowed) | ✅ library CI `ai-providers` job runs `ActionRecoveryTest` (not yet run) |
| Action recovery **default off** — intercepted click surfaces as `ElementClickInterceptedException` | ✅ `ActionRecoveryDisabledTest` (1, rule-based) | 🔎 library CI `browser` job |
| Assertion handling (`HEALER_ASSERTIONS=heal\|warn\|strict`; assert-absent never healed) | ✅ `AssertionHealingTest` (3) | ❌ |
| Two-tier heal cache (positive run-lifetime, negative 3s-TTL, `FAIL_COUNT`, `HEALED_LOCATORS`) | ✅ `HealCacheTest` (5) | 🔎 |
| Heal log `heals.jsonl` (round-trip, `newLocator`/`newFindBy`/`declarationLocation`) | ✅ `HealLogTest` (5) | ✅ written on every sample CI heal |
| `apply-heals` — inline `By.x()`, `@FindBy(...)`, `By` field-declaration, comment-skip, affected-test list | ✅ `ApplyHealsTest` (5 — incl. the declaration-line affected-test fix) | 🔎 sample CI `apply-heals` job |
| `apply-heals` full round-trip (heal → apply → verify with healing off) | ✅ verified end-to-end against the sample (heal via anthropic → `apply-heals --yes` rewrites 3 declaration lines → `verify-heals.sh` re-runs with healing off → passes) 2026-09-01 | ✅ sample CI `apply-heals` job (not yet run) |
| `doctor` CLI | 🔎 run by hand | ❌ not run in CI |
| HTML step report (`ReportRenderer`) | ✅ `ReportRendererTest` (5) | ❌ not generated in CI |
| Per-test attribution + per-test cache clear (`TamashReportListener`) | ✅ `FrameworkIntegrationTest`, `TestNgSmoke` | ✅ implicit in every sample CI test |
| JUnit 5 integration (`@UseTamashSelenium`) | ✅ used across the suite | ✅ sample `junit5/` |
| TestNG integration (`TamashSeleniumTestNgTest` / listener) | ✅ `TestNgSmoke` | ✅ sample `-Ptestng` |
| Cucumber integration (`TamashSeleniumCucumberHooks` / `Scenario`) | ✅ `smoke/` feature | ✅ sample `cucumber/` |
| Plain `SelfHealingDriver.wrap` (no framework) | ✅ `SampleTest` | ✅ sample `PlainWrapTest` |
| `Tamash.hint(String)` explicit description scope | ✅ `TamashHintTest` (2) | ✅ sample keyword-driven tests |
| Keyword-driven layer (`WebUtil` + hint) | ✅ `WebUtilHealingTest` | ✅ sample `KeywordDrivenTest` |
| HTTP retry (Retry-After header, Gemini `retryDelay` body, 8s cap, single retry) | ✅ `HttpRetryTest` (7 — local `com.sun.net.httpserver` stub) | 🔎 gemini leg of sample CI exercises it incidentally |
| Gemini `reasoning_effort: low` / `GEMINI_THINKING` toggle | 🔎 verified live (gemini-flash-lite ~0.7s/heal in CI) | ✅ sample `ai-providers` gemini leg |
| Optional-dependency-absent load (junit-jupiter-api / testng / cucumber off the classpath) | ✅ `OptionalDependencyAbsentTest` (4 — isolated classloader with each framework stripped; core `wrap` runs; each framework integration links without the other two) | 🔎 library CI `test` job |
| Provider: `tamash` | ✅ full suite | ✅ sample default |
| Provider: `openai` | ✅ `AiHealingE2ETest` + `ActionRecoveryTest` (gpt-4o-mini, 2026-09-01) | ✅ sample `ai-providers` + library CI `ai-providers` (not yet run) |
| Provider: `anthropic` | ✅ `AiHealingE2ETest` + `ActionRecoveryTest` (claude-haiku-4-5, 2026-09-01) — needs `ANTHROPIC_MODEL` set, no default | ✅ sample + library CI (not yet run) |
| Provider: `gemini` | ✅ `AiHealingE2ETest` + `ActionRecoveryTest` (gemini-flash-lite-latest, 2026-09-01) | ✅ sample + library CI (not yet run) |
| Provider: `ollama` | ✅ `AiHealingE2ETest` + `ActionRecoveryTest` (gpt-oss:120b, 2026-09-01) | ❌ not in CI (Phase 3) |
| Provider: `ollama-local` | ⚠️ same code path as `ollama`; not re-run against a live local server this round | ❌ |
| Provider: `claude-subscription` | ✅ `AiHealingE2ETest` + `ActionRecoveryTest` (claude-haiku-4-5, 2026-09-01) | ❌ not in CI (Phase 3) |
| Provider: `copilot-subscription` | ✅ `AiHealingE2ETest` (Copilot CLI 1.0.82, ~25s, 2026-09-01) | ❌ not in CI (Phase 3) |
| `init-skill` CLI + `doctor` Skill check | ✅ `SkillTest` (11 — real installs to a temp dir: both targets, `adapters/` excluded, idempotency, version bump, `--force`, `--dry-run`, state transitions, legacy detection) + verified end-to-end (`init-skill` both targets, idempotent re-run, `doctor` Skill row) | 🔎 library CI `package` job runs `doctor` |
| Skill content (`SKILL.md` + `references/`) | 🔎 authored; not yet handed to unbriefed agents the way pw's was | ❌ |
| Library repo CI | ✅ `.github/workflows/ci.yml` — `test` (unit + `-Pbrowser`), `package`+`doctor` smoke, `ai-providers` matrix. **Not yet pushed / run.** | — |
| `HealingScenariosTest` offline mirrors (waitFor-absent not healed) | ✅ folded into `WaitHealingTest` + `AssertionHealingTest` | 🔎 library CI `browser` job |

**What this table makes plain, on purpose**: the core heal loop and the framework integrations
are solid — unit-covered for the deterministic parts, and proven end-to-end in the sample repo's
CI against a live SPA with three AI providers. What is *not* covered: `<select>`, iframes, action
recovery, replay-argument forwarding, `getDurable`, the master switch as an asserted test, the
HTTP-retry and optional-dependency-absent paths, four of seven providers in CI, and the entire
`apply-heals`-in-CI / skill-install / library-CI surface. Those are the ❌ rows, tracked in the
roadmap.

---

## Per-release notes

### [unreleased] — test parity build-out — started 2026-09-01

Kicked off closing the gap to `tamash-playwright`'s verification bar (see roadmap). Landed so far:

- **Phase 1 unit tests** — added `ReportRendererTest` (5: summary/steps/heal-note, HTML escaping,
  empty-steps placeholder, unrecovered-failure DOM-snapshot block, token-chart placeholder),
  `TamashRuleBasedProviderTest` (9: `parseDescriptionForMatch` phrase/hint/suffix/empty,
  `suggestSelector` resolve + three decline paths, `suggestActionTactic` always `NONE`, provider
  name), deepened `PromptTest` from 4 → 15 (every `parseSuggestion` strategy shape, markdown-fenced
  JSON, missing-field rejection, `role`→css/xpath fold, snapshot truncation, Selenium-flavoured
  system prompts, `parseActionTacticSuggestion` all five wire values, usage extraction), and
  `HttpRetryTest` (7: Retry-After header short/long delay, Gemini `retryDelay` body field, bare
  429 fails fast, 5xx one-retry-then-give-up, 5xx-then-success — against a local
  `com.sun.net.httpserver` stub). `decodeVariableName` was found already covered in
  `SourceLocationsTest`.
- Unit total: ~43 → ~75, all green (`mvn test`, unit-only, ~5s).
- Still open in Phase 1: `OptionalDependencyAbsentIT` (maven-invoker).

- **Phase 0 infra** — `RELEASE-TESTING.md` (this file), `TESTING.md` rewritten to operating-manual
  depth, `.github/workflows/ci.yml` for the library repo (`test` job runs `mvn test` then
  `mvn test -Pbrowser`; `package` + `doctor` smoke; gated `ai-providers` matrix). New `browser`
  Maven profile un-excludes the rule-based browser suite so `mvn test` stays unit-only and fast.
  **CI not yet pushed or run.**
- **Phase 2 browser e2e** — `HealingDisabledTest` (2), `SelectHealingTest` (2), `IframeHealingTest`
  (1), `ReplayArgForwardingTest` (2), `GetDurableTest` (3), `ReportCacheHonestyTest` (1), and a
  never-appears-not-healed case added to `WaitHealingTest`. `mvn test -Pbrowser` = ~110 tests,
  all green (~2 min, headless Chrome, rule-based provider).
- **Phase 3 (partial)** — `AiHealingE2ETest` (3 broken locators via a live provider), verified
  locally against **openai** (gpt-4o-mini), **anthropic** (claude-haiku-4-5), **gemini**
  (gemini-flash-lite-latest). Found: `anthropic`/`openai` need `*_MODEL` set explicitly (no
  default → `stage=no_provider`); the persistent `heals.jsonl` is a cross-run cache that must be
  cleared or it serves `provider=cache` and no AI call happens (now documented in `TESTING.md`).
- **Phase 4 action recovery** — `ActionRecoveryTest` (opt-in flag + AI provider + a click covered
  by an overlay): the click is recovered cleanly against **openai, anthropic AND gemini**
  (Selenium's FORCE tactic is a JS `.click()`, which fires the button's `onclick` past the
  overlay — steadier than pw's experience). `ActionRecoveryDisabledTest` proves the default: with
  the flag off, an intercepted click surfaces as `ElementClickInterceptedException`, never
  swallowed.
- **Phase 5 skill migration** — `cli/Skill.java` + `init-skill` subcommand + `doctor` Skill check
  + `SkillTest` (11). The per-agent `adapters/` folder is deleted; one `SKILL.md` + `references/`
  installs into `.claude/skills/` and `.agents/skills/` (Playwright `install --skills`
  convention). Added a filtered `build.properties` resource so `getPackageVersion()` resolves
  when run from `target/classes` (`mvn exec:java`), not only from the packaged jar. Verified the
  clean-built jar carries no `adapters/`.
- **Phase 1 `OptionalDependencyAbsentTest`** (4) — isolated `URLClassLoader` with JUnit / TestNG /
  Cucumber jars stripped: core `SelfHealingDriver.wrap` runs; each framework integration links
  without the other two. No hard reference from the core path.
- **Phase 3** — all 7 providers now verified locally against `AiHealingE2ETest`
  (tamash/openai/anthropic/gemini/ollama/claude-subscription/copilot-subscription).
  `ActionRecoveryTest` additionally verified vs openai/anthropic/gemini/ollama/claude-subscription.
  Sample CI got an `ollama` leg and an `apply-heals` job. The `apply-heals` round-trip was run
  end-to-end against the sample and **surfaced a real bug** (empty affected-test list for a
  declaration-line rewrite) — fixed in `ApplyHeals.planFixes`, covered by `ApplyHealsTest`.
  `SelfHealingDemoTest` (both flavours) now guards its heal-metadata assertions behind
  `Healer.isHealingEnabled()` so `verify-heals` passes.
- Unit ~90, `mvn test -Pbrowser` ~123, all green.
- **Library CI ran for real, first time, fully green** (run `33528061152`, pushed as
  `0.1.0-beta.4`): `mvn test (unit + rule-based e2e)`, `package + doctor smoke`, and
  `Real-AI heal` for anthropic/gemini/openai — including `ActionRecoveryTest` inside the
  anthropic job (`actionRecovery=yes`, recovered).
- Repo relicensed **Apache License 2.0** and made **public**:
  https://github.com/qtpsudhakarproducts/tamash-selenium
- `0.1.0-beta.4` deployed and published to Maven Central (deployment
  `08acb36f-b5ea-47bc-af29-24f91a3a11d2`).
- **Sample repo CI ran for real, first time, fully green** (run `33528507241`): `TestNG`,
  `JUnit 5 + Cucumber`, `Heal demo` for openai/anthropic/gemini/ollama (all 4 — the ollama leg
  is new), and the new **`apply-heals round trip`** job — heal → `apply-heals --yes` → verify
  with healing off, all green. (Still on the published `0.1.0-beta.3`, so the verify step ran
  the workflow's fallback command rather than a generated `verify-heals.sh` — the underlying
  round trip is proven either way; re-verify with the real script once `0.1.0-beta.4` resolves.)
- Still open: bump the sample's dependency pin to `0.1.0-beta.4` once it resolves on Central and
  re-run to confirm the generated `verify-heals.sh` path specifically; hand the skill to
  unbriefed agents; `ollama-local` live re-check.
- **Non-Chrome browsers (Firefox/Edge/Safari) are explicitly out of scope for the test suite**
  (user decision, 2026-09-01) — Chrome is the one covered configuration. The lifecycle still
  supports `TAMASH_BROWSER=firefox|edge|safari`; those branches are just untested.

### [0.1.0-beta.3] — 2026-08-31

Gemini reliability: `reasoning_effort: low` on the Gemini surface, `DEFAULT_TIMEOUT_MS` 10s → 20s,
`Http` retries once only on a server-named short delay. Sample CI fully green (JUnit5+Cucumber,
TestNG, openai, anthropic, gemini) at `gemini-flash-lite-latest`.

### [0.1.0-beta.2] — 2026-08-30

Java 21, `junit-jupiter-api` optional, vision removed, `apply-heals` handles `By` field
declarations, `TAMASH_SOURCE_ROOTS` multi-module probing, per-test attribution, token-set
matching, attribute stacking, HTTP retry, Safari lifecycle path (**never run**).

### [0.1.0-beta.1] — 2026-08-30

Initial release — full-parity port from `tamash-playwright-java`. Unit suite + rule-based
browser tests green locally; validated against live OrangeHRM via the sample repo.
