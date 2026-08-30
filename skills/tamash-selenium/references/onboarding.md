# Onboarding a project to tamash-selenium's standards

Use this when `doctor` reports any `[WARN]`/`[FAIL]`, or when the `WebDriver` isn't wrapped yet. Work through the sections below in order — each is independent, so if only some apply, skip the rest. After every fix, the section tells you exactly how to confirm it actually worked — don't move on without checking.

**`[INFO]` rows are not part of this checklist.** `doctor` also reports purely observational rows — whether the configured model looks vision-capable by name, that `HEALER_PROVIDER` is unset and defaulting to the rule-based `tamash` provider, and how many locators sit inline across how many test files. None is a standard to meet or something to fix; they're context. Once every `[WARN]`/`[FAIL]` below is resolved and the driver is wrapped, the project is done here regardless of what those `[INFO]` rows still say.

## 1. The `WebDriver` isn't wrapped (doctor can't detect this — check it yourself)

This is the whole integration and the one thing `doctor` can't verify. Self-healing only happens for elements found through a wrapped driver.

Search the project for where the `WebDriver` is created (`new ChromeDriver`, `new RemoteWebDriver`, a `DriverFactory`, a `@BeforeMethod`/`@BeforeEach` setup, a Cucumber hook). Exactly one of these must be true:

- **Plain wrap** — the returned driver is passed through `SelfHealingDriver.wrap(...)` before any test or Page Object uses it:

  ```java
  import io.github.qtpsudhakarproducts.tamash.SelfHealingDriver;

  WebDriver driver = SelfHealingDriver.wrap(new ChromeDriver(options));
  ```

- **An integration owns the lifecycle** — the test gets its driver from `@UseTamashSelenium` (JUnit 5), `extends TamashSeleniumTestNgTest` (TestNG), or the Cucumber glue. Those wrap internally; nothing else to do.

If neither is true, add the plain wrap at the single point where the driver is handed out. Follow whatever the project already does — wrap the return value of the existing factory method rather than restructuring it. `SelfHealingDriver.wrap(...)` also pins Selenium's implicit wait to 0 (set `TAMASH_KEEP_IMPLICIT_WAIT=true` to keep the project's own implicit wait, but mixing implicit + explicit waits is a Selenium anti-pattern anyway).

**Confirm it worked**: add a throwaway test with a deliberately-broken locator on a page the suite already visits (e.g. change `By.id("username")` to `By.id("user_name")`), run it, and look for a `[self-healer] … -> HEALED` line on the console. Remove the throwaway test afterward. If nothing heals, the driver the test used isn't the wrapped one — trace the object back to its source.

## 2. AI provider — pick one (or stay on the free rule-based default)

`doctor`'s "AI Provider" section shows one of:

- **`[INFO] HEALER_PROVIDER is not set — defaulting to the rule-based tamash provider`** → not a problem. `tamash` needs no key, no network, no tokens, and never guesses. It's a good first line of defense for well-named, Page-Object-style suites. Only move to an AI provider if the user wants a higher recovery rate on harder cases. **Ask the user — don't assume.**
- **`[FAIL] HEALER_PROVIDER=<x>, but its required model/API key env vars are missing`** → the project asked for an AI provider and it's misconfigured. Fix it (below).
- **`[OK] Connected to <provider> successfully`** → done, skip this section.

Providers and their auth (set in a `.env` file at the project root, or as real env vars / `-D` system properties):

- **Free, fastest AI start**: Ollama Cloud — `HEALER_PROVIDER=ollama`, `OLLAMA_MODEL=gpt-oss:120b`, `OLLAMA_API_KEY=` (free key from `ollama.com/settings/keys`).
- **An API-key provider they already pay for**: `openai` (`OPENAI_API_KEY` + `OPENAI_MODEL`) / `anthropic` (`ANTHROPIC_API_KEY` + `ANTHROPIC_MODEL`) / `gemini` (`GEMINI_API_KEY` + `GEMINI_MODEL`).
- **A subscription they already have, no API key**: `claude-subscription` (`CLAUDE_CODE_OAUTH_TOKEN` from `claude setup-token`) or `copilot-subscription` (needs the optional `com.github:copilot-sdk-java` dep + the `copilot` CLI signed in; no vision).
- **Their own self-hosted Ollama**: `ollama-local` — same shape as `ollama` but `OLLAMA_LOCAL_BASE_URL` points at their server; `OLLAMA_LOCAL_API_KEY` optional.
- **Zero AI, no network**: `tamash` (the default) — nothing to configure.

Write (or update) `.env` from `.env.example` with their choice. **Never handle the real API key value yourself** — write the variable name with an empty value and have the user paste the real key into the file directly. Never echo a key back in chat, a commit message, a log line, or a report. Make sure `.env` is in `.gitignore`.

**Confirm it worked**: re-run `mvn -q exec:java -Dexec.args="doctor"`. The AI Provider section must show `[OK] Connected to <provider> successfully.` A `[FAIL]` names the real failure (bad key, unreachable host, model not found) — read the actual error line. Don't move on until it's `[OK]` (or the user has decided to stay on `tamash`).

## 3. Brittle locators with no descriptive name

`doctor` lists these in a table:

```
[WARN] Found 4 brittle CSS/XPath locator(s) with no descriptive variable name:
  Location                              Snippet
  src/test/java/.../LoginTest.java:24   driver.findElement(By.cssSelector("input[name='user']"))
  ...
```

tamash-selenium derives the element's human description from the **name of the `By` variable / `@FindBy` field at the call site** (`usernameTextbox` → "Username (textbox)"). A raw `By.cssSelector("div > input:nth-child(2)")` passed inline, or bound to `loc1`, gives the healer nothing to work with.

For each flagged locator:

1. Open the file at the given location.
2. **Read the actual context** — the surrounding page/component, not just the existing name — to choose a genuinely accurate, human-readable name. This is the one place this skill should do better than the package's own automatic fallback (`decodeVariableName`, which only has the raw identifier): you can look at what the element actually *is*.
3. Bind the locator to a well-named `private final By` field (or `@FindBy` field), e.g. `private final By firstNameTextbox = By.cssSelector("input[name='firstName']");`. This is a pure rename/extract — no runtime behavior change — safe to apply across every flagged locator in one pass without asking per-locator. Summarize what you changed when done.
4. For a keyword/`WebUtil` layer where the name genuinely can't reach the call site, wrap the action in `try (var s = Tamash.hint("First name field")) { ... }` instead — see USAGE.md's `Tamash.hint` section.

**Confirm it worked**: re-run `doctor` — the count under "Found N brittle … locator(s)" should drop to reflect what you fixed (0 if you did all of them).

## 4. Locators written directly in test files (should be in a Page Object)

`doctor` reports this as `[INFO]`, not `[WARN]` — inline locators still heal fine, this is about long-term maintainability, not correctness. **Unlike the naming fix above, do not mechanically extract every one without asking first.** This is a real structural refactor, and every project already has (or lacks) its own Page Object conventions.

Before touching anything:

1. Look for an existing Page Object pattern in the project (a `pages/` / `pom/` package, a base page class, an established naming convention). Follow whatever already exists — don't invent a new structure if one is there.
2. **Before assuming a flagged file is an oversight, check whether it's deliberately inline** — a comment like `// non-POM example`, a filename like `*InlineTest.java` or `*DemoTest.java`, or a project README/AGENTS.md section describing it as a teaching/contrast example. A project can have a complete Page Object layer *and* still correctly leave a few files inline on purpose.
3. If no existing convention is found at all, propose one and confirm it with the user before creating it.
4. Ask the user for scope: every flagged locator that's actually in scope after step 2, or a small sample first to confirm the approach.

**Confirm it worked**: re-run `doctor` after the agreed scope is done — the inline-locator count should reflect exactly what was moved, no more, no less.

## 5. TestNG-only projects: the Surefire "Tests run: 0" trap

If the project uses **TestNG and not JUnit 5**, and `mvn test` suddenly reports `Tests run: 0` after adding `tamash-selenium`: Surefire auto-detected the JUnit Platform provider because `tamash-selenium` brings `junit-jupiter-api` onto the classpath. Fix it by excluding that transitive dep on the `tamash-selenium` dependency:

```xml
<dependency>
  <groupId>io.github.qtpsudhakarproducts</groupId>
  <artifactId>tamash-selenium</artifactId>
  <version>0.1.0-beta.1</version>
  <exclusions>
    <exclusion>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

(A JUnit 5 or mixed project needs `junit-jupiter-api` and must not exclude it.)

**Confirm it worked**: `mvn test` runs the project's TestNG tests again (`Tests run:` > 0).

## Done

Once `doctor` reports no remaining `[WARN]`/`[FAIL]` (any `[INFO]` rows are fine) **and** the driver is wrapped (section 1), the project meets tamash-selenium's standards. If the suite has already been run with healing enabled since then, continue to [heal.md](heal.md) to review what healed. Otherwise, onboarding is complete — no further action needed here.
