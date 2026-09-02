# tamash-selenium

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.vibetestq.qtpsudhakar/tamash-selenium.svg)](https://central.sonatype.com/artifact/com.vibetestq.qtpsudhakar/tamash-selenium)
[![Docs](https://img.shields.io/badge/docs-site-1a73e8.svg)](https://qtpsudhakarproducts.github.io/tamash-selenium/)

📖 **[Full documentation & support →](https://qtpsudhakarproducts.github.io/tamash-selenium/)**

Plug-and-play self-healing for **Selenium Java**. Wrap your `WebDriver` once and every
`findElement` through it — in Page Objects, `@FindBy` fields, helper/util layers, inside a
`WebDriverWait` — recovers automatically when a locator breaks. Nothing else changes.

```java
WebDriver driver = SelfHealingDriver.wrap(new ChromeDriver());
```

That's the whole integration.

> Also available for Playwright — TypeScript ([`tamash-playwright` on npm](https://www.npmjs.com/package/tamash-playwright)),
> Python (`tamash-playwright` on PyPI), and Java. Same idea, separate package per ecosystem.

## Why you need this

Websites change. A button gets renamed or moved and your test can't find it anymore — even though
the app works fine for real users. Normally that's a broken test.

`tamash-selenium` fixes it: when `findElement` can't find an element, it locates it on the live
page — with a rule-based matcher (default, no key, no network) or an AI model — and retries. If it
can't, the test fails exactly as it would have without the package. Healing never masks a real
failure.

## Step 1: Install

```xml
<dependency>
  <groupId>com.vibetestq.qtpsudhakar</groupId>
  <artifactId>tamash-selenium</artifactId>
  <version>0.2.0</version>
</dependency>
```

Pulls in Selenium 4 transitively. Selenium 4.6+ provisions the browser drivers itself. Requires
**Java 21+** at runtime.

## Step 2: Wrap the driver

Wherever you create the driver — your `BaseTest`, a `DriverFactory`, a `@BeforeMethod`:

```java
import com.vibetestq.qtpsudhakar.tamash.SelfHealingDriver;

WebDriver driver = SelfHealingDriver.wrap(myDriver);   // RemoteWebDriver / Grid / cloud all fine
```

Everything downstream is now healing-aware:

```java
// By-field Page Objects — no change
driver.findElement(loginButton).click();

// @FindBy / PageFactory — no change (plain PageFactory.initElements with the wrapped driver)
PageFactory.initElements(driver, loginPage);
loginPage.usernameField.sendKeys("admin");

// your WebUtil / wait helpers — no change
new WebDriverWait(driver, ofSeconds(10))
    .until(ExpectedConditions.elementToBeClickable(loginButton)).click();
```

Wrapping also pins Selenium's implicit wait to 0 (mixing implicit + explicit waits is a Selenium
anti-pattern, and a high implicit wait delays healing) — set `TAMASH_KEEP_IMPLICIT_WAIT=true` to
keep yours.

## Step 3 (optional): connect an AI provider

With no configuration, healing uses the rule-based **`tamash`** provider — no key, no network, no
tokens; it text-matches the element's decoded name against the page's accessibility tree and never
guesses. Good for well-named suites.

For stronger healing (semantic reasoning), set a provider in a `.env` at your
project root:

| Provider | Auth | Notes |
|---|---|---|
| `ollama` | `OLLAMA_API_KEY` + `OLLAMA_MODEL` | Ollama Cloud — free key, fastest start |
| `ollama-local` | `OLLAMA_LOCAL_MODEL` (key optional) | Your own `ollama serve` |
| `openai` | `OPENAI_API_KEY` + `OPENAI_MODEL` | |
| `anthropic` | `ANTHROPIC_API_KEY` + `ANTHROPIC_MODEL` | |
| `gemini` | `GEMINI_API_KEY` + `GEMINI_MODEL` | |
| `claude-subscription` | `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token`) | Bills your Claude subscription |
| `copilot-subscription` | `com.github:copilot-sdk-java` + `copilot` CLI | Optional dependency |
| `tamash` | **nothing** | The default |

```sh
HEALER_PROVIDER=ollama
OLLAMA_MODEL=gpt-oss:120b
OLLAMA_API_KEY=paste_your_key_here
```

`HEALER_ENABLED=false` turns healing off entirely (e.g. per-CI-run). Env vars, `-D` system
properties, and `.env` are all read (that precedence).

## Step 4: Check your setup

```sh
mvn exec:java -Dexec.args="doctor"
```

Provider connectivity (a live call), the implicit-wait setting, and a scan for
brittle locators bound to non-descriptive names.

## The optional conveniences

You don't need any of these — wrapping the driver is enough. They add polish:

| | What it adds |
|---|---|
| `@UseTamashSelenium` (JUnit 5) / `extends TamashSeleniumTestNgTest` / the Cucumber glue package | Manages the driver lifecycle for you and attributes each heal to its test / renders the HTML step report |
| `TamashPageFactory.initElements(driver, page)` (instead of plain `PageFactory`) | The healer's description for a `@FindBy` field becomes the **field name** rather than the raw selector — better hints |
| Descriptive locator variable / field names (`usernameTextbox`, `loginButton`) | Decoded to "Username (textbox)" etc. as the healer's description — no code, just naming |
| `Bindings.getDurable(driver, by)` | Upgrade a brittle XPath to a stable `By` up front |

## How it heals

When `findElement` can't find its element:

1. **Cache** — a selector already healed this run for that locator (any caller, including the
   wait's next poll) is reused instantly. A wait's repeated polls cost ~one heal, not one per
   poll.
2. **DOM snapshot** — a JS accessibility tree of the page is captured; the provider is shown the
   relevant slice (matched to the element's decoded name), or the full tree.
3. **`ref` + durable derivation** — the provider picks the element; a stable `By` is derived for
   it (`By.id` → `By.name` → a `data-testid` / `aria-label` CSS → link text → a structural XPath),
   verified against the live element before it's trusted.
4. **Action recovery** (opt-in, `HEALER_ACTION_RECOVERY_ENABLED=true`) — scroll / JS-click / wait
   / dispatch when the element is found but the action is blocked.

Every heal logs `[self-healer] … -> HEALED [provider=…, suggested="By.…"]`.

## Adopting into an existing suite

| You have | Do |
|---|---|
| A `BaseTest` / `DriverFactory` | Add `driver = SelfHealingDriver.wrap(driver)` on one line |
| A `BasePage` calling `PageFactory.initElements` | Nothing — it heals through the wrapped driver. (Swap to `TamashPageFactory` for field-name descriptions.) |
| A `WebUtil` / keyword layer wrapping `WebDriverWait` | Nothing — waits heal |
| A custom framework, no TestNG/JUnit/Cucumber | Wrap the driver; optionally call `CurrentTest.set(...)` / `TamashReport.*` per test for heal attribution + the report (plain static calls — see USAGE.md) |
| A high implicit wait in your base | It's pinned to 0 on wrap; `TAMASH_KEEP_IMPLICIT_WAIT=true` to keep yours (not recommended) |

## Assertions

A broken locator inside an assertion heals — a locator is test plumbing, the assertion (does the
page show the right thing?) is the intent, and healing separates the two. Healed assertions are
logged distinctly (`[self-healer][assertion] …`).

- **`HEALER_ASSERTIONS=warn`** — still heals, but flags each affected test and prints a summary at
  the end. For teams who want CI to keep moving but a healed assertion to be a signal.
- **`HEALER_ASSERTIONS=strict`** — a broken locator inside an assertion is a real failure; don't
  heal it.
- **"Assert absent"** — `assertThrows(NoSuchElementException.class, …)`,
  `invisibilityOfElementLocated`, `stalenessOf` — is **never** healed, in any mode (a heal would
  defeat the assertion). Use `driver.findElements(by).isEmpty()` for absence checks — `findElements`
  is never healed.

## Making a heal permanent: `apply-heals`

A runtime heal fixes the current run; `apply-heals` writes it into source:

```sh
mvn exec:java -Dexec.args="apply-heals --dry-run"   # preview
mvn exec:java -Dexec.args="apply-heals"             # apply (prompts first)
#  By.cssSelector("#old")           →  By.id("username")
#  @FindBy(css = "#old")            →  @FindBy(id = "username")
```

It rewrites an inline `By.xxx("…")` literal or a `@FindBy(...)` annotation on the recorded line,
writes Markdown + JSON reports under `.tamash-selenium/`, and generates `verify-heals.sh` / `.cmd`
that re-runs exactly the affected tests with `HEALER_ENABLED=false`. A locator kept in a separate
`private final By field = ...` (the call site references only `field`) has no literal to rewrite —
that heal is reported *skipped*; the entry's `newLocator` / `newFindBy` in `heals.jsonl` is the
exact replacement text to paste in.

## HTML step report

```sh
mvn test -DTAMASH_REPORT=target/tamash-report.html
```

Per test: step timeline, which steps healed (recovered selector, provider, token cost), the DOM
snapshot on an unrecovered failure. Needs one of the framework integrations for per-test grouping;
zero overhead when `TAMASH_REPORT` is unset.

## Agent skill

The JAR bundles a coding-agent skill (`SKILL.md` + `references/`) that drives the local
run → review → `apply-heals` → verify → land loop. Install it into a project:

```sh
mvn exec:java -Dexec.args="init-skill"
```

That copies it into both `.claude/skills/tamash-selenium/` (Claude Code) and
`.agents/skills/tamash-selenium/` (the cross-tool standard — Cursor, Copilot, Windsurf, Kiro, …).
`--target claude|agents` installs one; `--user` installs for every project on the machine.
`mvn exec:java -Dexec.args="doctor"` reports whether the installed copy is current.

## License

[Apache License, Version 2.0](LICENSE) — free to use, modify, and redistribute, including
commercially, as long as you keep the copyright and license notices (see [NOTICE](NOTICE)).
Contributions welcome.

## Support

support@vibetestq.com
