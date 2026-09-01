<!--
  Append this section to the project's .github/copilot-instructions.md
  (or drop it in as the whole file if one doesn't exist yet).
-->

## tamash-selenium — self-healing Selenium Java workflow

This project uses `tamash-selenium`. Before changing test files, Page Objects, `WebDriver` setup, or anything under `.tamash-selenium/`:

1. Run `mvn -q exec:java -Dexec.args="doctor"` first — always, don't assume the project's current state. (Use `./mvnw` if there's a wrapper; run from the module with the `tamash-selenium` dependency.)
2. Any `[FAIL]`/`[WARN]`, or the `WebDriver` isn't wrapped with `SelfHealingDriver.wrap(...)` / a `tamash-selenium` integration (doctor can't detect the wrap — check the driver setup yourself) → bring the project up to standard. Extract the onboarding guide with `mvn -q dependency:unpack -Dartifact=io.github.qtpsudhakarproducts:tamash-selenium:0.1.0-beta.2 -Dmdep.unpack.includes="skills/**" -DoutputDirectory=target/tamash-skill` and follow `target/tamash-skill/skills/tamash-selenium/references/onboarding.md` (AI provider setup, wrapping the driver, descriptive locator names, Page Object extraction, the TestNG "Tests run: 0" Surefire trap — each with how to confirm the fix worked).
3. All `[OK]`, driver wrapped, and the suite has run recently with healing on → follow `references/heal.md` from the same path to review, apply, verify, and land whatever healed. It's a gated loop: most runs go start to finish with no interruption, but it always pauses before landing anything — never commit or open a pull request on its own.
