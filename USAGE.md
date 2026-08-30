# tamash-selenium — Usage Guide

Self-healing for Selenium Java. When `findElement` can't find its element, a configured AI model
(or the rule-based `tamash` provider) locates it on the live page and the call is retried. If that
fails too, the test fails exactly as it would have without the package — healing never masks a real
failure.

For a shorter overview see [README.md](README.md). This file is the complete reference.

## Contents

- [1. Install](#1-install)
- [2. Connect a provider](#2-connect-a-provider)
- [3. The healing model (read this)](#3-the-healing-model-read-this)
- [4. Check your setup: `doctor`](#4-check-your-setup-doctor)
- [5. Use it in your tests](#5-use-it-in-your-tests)
- [Writing locators the healer can work with](#writing-locators-the-healer-can-work-with)
- [What gets healed (and what doesn't)](#what-gets-healed-and-what-doesnt)
- [Getting a durable locator directly: `Bindings.getDurable`](#getting-a-durable-locator-directly-bindingsgetdurable)
- [Not paying for the same heal twice](#not-paying-for-the-same-heal-twice)
- [Making a heal permanent: `apply-heals`](#making-a-heal-permanent-apply-heals)
- [HTML step report](#html-step-report)
- [Environment variables](#environment-variables)
- [CLI commands](#cli-commands)

---

## 1. Install

```xml
<dependency>
  <groupId>io.github.qtpsudhakarproducts</groupId>
  <artifactId>tamash-selenium</artifactId>
  <version>0.1.0-beta.1</version>
</dependency>
```

Pulls in Selenium 4 and JUnit 5 transitively. Selenium 4.6+ provisions the browser drivers itself.

**TestNG and Cucumber** are optional integrations — this package declares `org.testng:testng` and
`io.cucumber:cucumber-java` as `provided`, so add whichever your project already uses.

---

## 2. Connect a provider

Create a `.env` in your project root (or set the same names as real env vars / `-D` system
properties). All three are read, in this precedence: **OS env var → `-D` system property → `.env`**.

```sh
HEALER_ENABLED=true
HEALER_PROVIDER=ollama

OLLAMA_MODEL=gpt-oss:120b
OLLAMA_API_KEY=paste_your_key_here
```

| Provider | Auth | Notes |
|---|---|---|
| `ollama` | `OLLAMA_API_KEY` + `OLLAMA_MODEL` | Ollama Cloud — free key, fastest start |
| `ollama-local` | `OLLAMA_LOCAL_MODEL` (key optional) | Your own `ollama serve` / internal deployment |
| `openai` | `OPENAI_API_KEY` + `OPENAI_MODEL` | |
| `anthropic` | `ANTHROPIC_API_KEY` + `ANTHROPIC_MODEL` | |
| `gemini` | `GEMINI_API_KEY` + `GEMINI_MODEL` | |
| `claude-subscription` | `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token`) | Bills your Claude subscription |
| `copilot-subscription` | `com.github:copilot-sdk-java` + `copilot` CLI signed in | Optional dependency; no vision |
| `tamash` | **nothing** | Rule-based, no AI, no network, no tokens |

### No AI at all: `HEALER_PROVIDER=tamash`

Resolves a broken locator by text-matching its decoded description against the page's DOM
accessibility snapshot, plus the same `near` / `adjacent` structural widening the AI providers use.
No key, no network, no tokens. The tradeoff: it never guesses — an ambiguous or weak match declines
rather than picks, and there's no vision or action-recovery fallback. A good fast first line of
defense for well-named, Page-Object-style suites.

---

## 3. The healing model

Every `findElement` that goes through the wrapped driver is healing-aware:

| Situation | Healed? |
|---|---|
| `driver.findElement(brokenBy)` / `element.findElement(brokenBy)` | ✅ at find time |
| `@FindBy` field, plain `PageFactory.initElements(wrappedDriver, page)` | ✅ (resolves through the wrapped driver) |
| Broken locator inside `wait.until(...)` — `ExpectedConditions`, custom conditions | ✅ (first few polls deferred as "still loading", then it heals; `HealCache` keeps the rest of the polls free) |
| A `WebUtil` / keyword layer wrapping any of the above | ✅ |
| `StaleElementReferenceException` acting on an element whose page changed | ✅ (re-find with the original locator first, then heal) |
| `ElementNotInteractableException` / `ElementClickInterceptedException` | ⚙️ action recovery (opt-in, `HEALER_ACTION_RECOVERY_ENABLED=true`) |

`SelfHealingDriver.wrap(...)` pins Selenium's **implicit wait to 0** (`TAMASH_KEEP_IMPLICIT_WAIT=true`
to keep yours). Use explicit `WebDriverWait` for synchronisation as normal.

`TAMASH_ACTION_TIMEOUT_MS` (default 10000) bounds the healer's own snapshot / JS / screenshot
calls, not your test's waits.

### Assertions

A broken locator inside `assertEquals(..., driver.findElement(x).getText())` heals — the assertion
is checking page content, not whether that selector string is current. Healed assertion finds log
as `[self-healer][assertion] …`.

| `HEALER_ASSERTIONS` | Behaviour |
|---|---|
| unset / `heal` | heal normally |
| `warn` | heal, flag the test, print a summary of affected tests at JVM shutdown |
| `strict` | a broken locator inside an assertion fails natively — no heal |

**Assert-absent is never healed** (any mode): `assertThrows(NoSuchElementException.class, …)`,
`ExpectedConditions.invisibilityOfElementLocated` / `stalenessOf` / `numberOfElementsToBeLessThan`,
and helper method names containing `absent` / `notPresent` / `gone`. A hand-rolled
`boolean isElementPresent(By)` that swallows `NoSuchElementException` can't be detected — use
`driver.findElements(by).isEmpty()` (never healed) for absence checks.

### Cost inside a wait

A wait polls `findElement` many times. The first few failures for a not-yet-seen locator are
deferred (~3s) with no snapshot/provider call — a still-loading page usually resolves within that
window. After that, the **heal cache** means only one poll captures a snapshot / calls the provider;
every later poll (and any other caller, that run) reuses the healed `By` instantly. A heal that
*declines* is remembered against the current DOM state, so a genuine "element not there yet" lets
the wait poll normally rather than re-paying each time. While in a wait, repeated non-heal lines are
suppressed until a locator has failed well into the wait (then one line surfaces, so a genuinely
broken locator isn't a silent timeout). With `HEALER_PROVIDER=tamash` there's no per-heal cost anyway.

---

## 4. Check your setup: `doctor`

```sh
mvn exec:java -Dexec.args="doctor"
```

Checks: AI connectivity (actually calls the provider), the implicit-wait setting, vision
capability, brittle CSS/XPath locators with a non-descriptive variable name, and locators declared
inline in test files (`--dir <path>` to scan elsewhere).

---

## 5. Use it in your tests

Whichever runner you use, the rule is the same: **get the `WebDriver` from the integration, write
your test normally.** Every element found through it is healing-aware.

### JUnit 5

```java
import io.github.qtpsudhakarproducts.tamash.junit.UseTamashSelenium;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

@UseTamashSelenium
class LoginTest {
  @Test
  void logsIn(WebDriver driver) {
    driver.get("https://the-internet.herokuapp.com/login");
    driver.findElement(By.id("username")).sendKeys("tomsmith");
    driver.findElement(By.id("password")).sendKeys("SuperSecretPassword!");
    driver.findElement(By.cssSelector("button[type='submit']")).click();
  }
}
```

`WebDriver`, `JavascriptExecutor`, and `TakesScreenshot` are all injectable (same instance). Fresh
driver per method by default; `TAMASH_REUSE_DRIVER=true` for one per class.

#### `@FindBy` / PageFactory

Swap `PageFactory.initElements` for `TamashPageFactory.initElements` in your Page Object
constructor — the only change:

```java
import io.github.qtpsudhakarproducts.tamash.pagefactory.TamashPageFactory;

public class LoginPage {
  @FindBy(id = "username")               WebElement usernameTextbox;
  @FindBy(css = "button[type='submit']") WebElement loginButton;

  public LoginPage(WebDriver driver) {
    TamashPageFactory.initElements(driver, this);
  }
}
```

`@FindBy` / `@FindBys` / `@FindAll` on `WebElement` fields heal; the healer's description is the
field name, decoded (`usernameTextbox` → "Username (textbox)"). Resolution stays lazy, so
`@CacheLookup` and `AjaxElementLocatorFactory` still work
(`new TamashFieldDecorator(driver, new AjaxElementLocatorFactory(driver, 10))` +
`PageFactory.initElements(decorator, page)` for a custom factory). `apply-heals` rewrites the
`@FindBy(...)` annotation. `@FindBy List<WebElement>` fields use Selenium's default (unhealed).

### TestNG

Extend `TamashSeleniumTestNgTest`, use `protected WebDriver driver`. The listener auto-registers
via `ServiceLoader` — no `@Listeners`, no `testng.xml` entry.

```java
public class LoginTest extends TamashSeleniumTestNgTest {
  @Test public void logsIn() {
    driver.get("https://the-internet.herokuapp.com/login");
    driver.findElement(By.id("username")).sendKeys("tomsmith");
  }
}
```

Add your existing TestNG dependency (shipped `provided`). If your suite already has a mandatory
base class, copy the four `@Before/@AfterClass/Method` methods out of `TamashSeleniumTestNgTest`
and keep the `ServiceLoader`-registered listener.

### Cucumber

Add `io.github.qtpsudhakarproducts.tamash.cucumber` to your `glue`.

```java
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
    value = "com.acme.steps,io.github.qtpsudhakarproducts.tamash.cucumber")
class RunCucumberTest {}
```

```java
import static io.github.qtpsudhakarproducts.tamash.cucumber.TamashSeleniumScenario.driver;

public class LoginSteps {
  @When("I sign in as {string} / {string}")
  public void signIn(String user, String pass) {
    driver().findElement(By.id("username")).sendKeys(user);
    driver().findElement(By.id("password")).sendKeys(pass);
    driver().findElement(By.cssSelector("button[type='submit']")).click();
  }
}
```

Hooks run at `order = 0`. Per-scenario heals are attached via `scenario.attach(...)`.

---

## Writing locators the healer can work with

`By.id(...)` / `By.name(...)` carry their own meaning. A raw `By.cssSelector(...)` / `By.xpath(...)`
doesn't — bind it to a descriptive field / variable and the healer decodes the name:
`txtEmployeeId` → "Employee Id (textbox)", `submitButton` → "Submit (button)" (deterministic, no
AI). Decoding works when the locator is on the same line as its `findElement` call, or is a
`@FindBy` / Page Object field the call references by name. When nothing decodes, the healer falls
back to the raw selector text.

The AI providers *also* receive the raw undecoded name, the broken selector, and the enclosing
class name, so even a terse `txtUsrNm` gives the model something to expand — the deterministic
decode is just the primary hint (and the only input the rule-based `tamash` provider uses).

**Through a `WebUtil` layer**: when a locator is passed into a helper —
`WebUtil.click(driver, loginButton)` — the name is resolved from the *caller's* line
(`loginButton`), not from the util's `by` parameter, by walking up the stack. Give util-call
arguments locator-ish names (`loginButton`, `usernameField`, `txtEmail`) and this works
automatically; the heal is recorded against the Page Object, not the shared util.

**Enum-driven / fluent-chain locators**: `LoginPage.txtUserName.enterText("admin")` or
`loginPage.usernameField().sendKeys(...)` — the enum constant / accessor name (`txtUserName`,
`usernameField`) is picked up from the call-site line and decoded, no code change.

**Explicit hint** — `Tamash.hint(...)` — for keyword-driven suites, opaque names (`txtSSN`), or
heavy indirection where no usable name reaches the call site. Set it where your framework already
carries the element's logical name:

```java
// your shared wrapper, e.g. SeleniumWrapper.click(By locator, String name)
public static void click(By locator, String name) {
  try (var h = Tamash.hint(name)) {          // ← the only addition
    getElement(locator).click();
  }
}
```

The hint takes precedence over the automatic decode and is passed to the AI provider as the
element's name. Bare `Tamash.hint(name)` (no try) also works — it's overwritten by the next hint
and cleared per test — but the try-with-resources form is safest. It does **not** change whether a
heal is attempted (assert-absent / `HEALER_ASSERTIONS=strict` still apply).

---

## What gets healed (and what doesn't)

Intercepted on `WebElement`: `click`, `sendKeys`, `clear`, `submit`, and the read methods
(`getText`, `getAttribute`, `getDomAttribute`, `getDomProperty`, `getCssValue`, `isDisplayed`,
`isEnabled`, `isSelected`, `getTagName`, `getAccessibleName`, `getAriaRole`, `getRect`). Plus
`findElement` / `findElements` on the driver and on elements.

Not touched: the `Actions` (advanced interactions) API, `Select` internals beyond the `WebElement`
it wraps (that still works through the proxy), and anything inside a `WebDriverWait`.

---

## Getting a durable locator directly: `Bindings.getDurable`

```java
import io.github.qtpsudhakarproducts.tamash.bindings.Bindings;

By durable = Bindings.getDurable(driver, By.xpath("//div[3]/form/input[2]"));   // or (driver, by, "sendKeys")
driver.findElement(durable).sendKeys("value");
```

Resolves the given `By` to an element and derives the most durable `By` for it. Throws if nothing
durable could be derived.

---

## Not paying for the same heal twice

A successful heal is remembered in `.tamash-selenium/heals.jsonl`. Next time that exact locator
breaks the same way, the confirmed selector is tried **first** — no snapshot, no AI call. The cache
persists on disk locally across runs; in CI it only helps within a single run. Landing the real fix
with `apply-heals` is what eliminates repeat AI calls in CI.

Each line records the broken `initialSelector`, the structured `suggestion` (the source of truth
`apply-heals` re-derives from), and — for quick human review — the rendered `newLocator`
(`By.name("firstName")`) and `newFindBy` (`@FindBy(name = "firstName")`) forms. A one-shot
ref/vision heal that couldn't be reduced to a durable selector has no `suggestion`/`newLocator` and
carries `needsReview: true` with a `reviewNote`.

---

## Making a heal permanent: `apply-heals`

```sh
mvn test                                            # heals at runtime, records what it healed
mvn exec:java -Dexec.args="apply-heals --dry-run"   # preview the By.… rewrite
mvn exec:java -Dexec.args="apply-heals"             # apply it (prompts first, at a real terminal)
```

Rewrites a `By.xxx("…")` literal or a `@FindBy(...)` annotation **on the recorded line**. Each run
writes `apply-heals-report.json` / `.md` under `.tamash-selenium/` (timestamped copies under
`history/`) and a `verify-heals` script (`.sh` / `.cmd`) that re-runs exactly the affected tests
with `HEALER_ENABLED=false`.

Flags: `--dry-run`, `--logs-dir <path>` (merge sharded `heals.jsonl` files), `--yes` / `-y`.

> **Beta limitation.** The heal is recorded at the `findElement` / action call site. So `apply-heals`
> lands automatically when the selector lives there — an inline `driver.findElement(By.id("…"))` or a
> `@FindBy` field. When a Page Object holds the locator in a separate `private final By field = By.id("…")`
> and the call site only references `field`, that line has no literal to rewrite and it is reported as
> *skipped*. Runtime healing still works; apply the fix by hand from the entry's `newLocator` /
> `newFindBy` in `.tamash-selenium/heals.jsonl` (both are the exact replacement text).

---

## HTML step report

```sh
mvn test -DTAMASH_REPORT=target/tamash-report.html
```

Per-test step timeline (action, duration, entered value), which steps healed (recovered selector,
provider, token cost), the DOM snapshot on an unrecovered failure, summary charts. Works for all
three runners; zero overhead when `TAMASH_REPORT` is unset.

---

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `HEALER_ENABLED` | `true` | Master switch. Any value other than `false` / `0` leaves healing on. |
| `HEALER_PROVIDER` | `tamash` | `ollama` \| `ollama-local` \| `openai` \| `anthropic` \| `gemini` \| `claude-subscription` \| `copilot-subscription` \| `tamash`. Unset = the rule-based `tamash`. |
| `HEALER_ASSERTIONS` | `heal` | `heal` \| `warn` (flag + end-of-run summary) \| `strict` (fail natively inside assertions). Assert-absent is never healed. |
| `TAMASH_KEEP_IMPLICIT_WAIT` | `false` | `true` keeps your implicit wait (wrapping pins it to 0 otherwise). |
| `OLLAMA_MODEL` / `OLLAMA_API_KEY` / `OLLAMA_BASE_URL` | — / — / `https://ollama.com` | Ollama Cloud. |
| `OLLAMA_LOCAL_MODEL` / `OLLAMA_LOCAL_BASE_URL` / `OLLAMA_LOCAL_API_KEY` | — / `http://localhost:11434` / — | Self-hosted Ollama. Key optional. |
| `OPENAI_MODEL` / `OPENAI_API_KEY` | — | OpenAI. |
| `ANTHROPIC_MODEL` / `ANTHROPIC_API_KEY` | — | Anthropic (Claude), API key. |
| `GEMINI_MODEL` / `GEMINI_API_KEY` | — | Google Gemini. |
| `CLAUDE_CODE_OAUTH_TOKEN` / `CLAUDE_SUBSCRIPTION_MODEL` | — / `claude-haiku-4-5` | `claude-subscription`. |
| `COPILOT_SUBSCRIPTION_MODEL` | — | `copilot-subscription`. |
| `HEALER_ACTION_RECOVERY_ENABLED` | `false` | Opt-in AI action recovery (`scroll` / `force` / `wait` / `dispatch`). |
| `TAMASH_BROWSER` | `chrome` | `chrome` \| `firefox` \| `edge`. |
| `TAMASH_REUSE_DRIVER` | `false` | Reuse one driver per test class instead of one per method. |
| `HEADLESS` | `true` | `false` runs the browser headed. |
| `TAMASH_ACTION_TIMEOUT_MS` | `10000` | Bounds the healer's own snapshot / JS / screenshot calls. |
| `TAMASH_REPORT` | unset | Output path for the HTML step report. |
| `TAMASH_DEBUG` | unset | Print DOM-snapshot capture diagnostics to stderr. |

---

## CLI commands

| Command | Flags | What it does |
|---|---|---|
| `mvn exec:java -Dexec.args="doctor"` | `--dir <path>` | Pre-flight checks: connectivity, implicit wait, vision, locator naming, inline locators. |
| `mvn exec:java -Dexec.args="apply-heals"` | `--dry-run`, `--logs-dir <path>`, `--yes` | Rewrite healed locators into source, write reports + a verify script. |

---

## License

See the [LICENSE](LICENSE) file. For questions, contact support@vibetestq.com.
