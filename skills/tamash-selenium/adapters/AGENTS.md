# tamash-selenium

This project uses `tamash-selenium` for self-healing Selenium Java tests. If you're working on test files, Page Objects, driver setup, or anything under `.tamash-selenium/`, follow this before making changes:

1. Run `mvn -q exec:java -Dexec.args="doctor"` first — always, don't assume the project's state. (Use `./mvnw` if there's a wrapper; run it from the module with the `tamash-selenium` dependency.)
2. **Any `[FAIL]`/`[WARN]`** (provider misconfigured, brittle CSS/XPath locators with no descriptive name), **or** the `WebDriver` isn't wrapped with `SelfHealingDriver.wrap(...)` / a `tamash-selenium` integration (doctor can't detect this — check the driver setup yourself) → the project hasn't adopted tamash-selenium's standards yet. Follow the onboarding reference (extract it with `mvn -q dependency:unpack -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.3 -Dmdep.unpack.includes="skills/**" -DoutputDirectory=target/tamash-skill`, then read `target/tamash-skill/skills/tamash-selenium/references/onboarding.md`).
3. **All `[OK]`**, driver wrapped, and the suite has run recently with healing enabled → review, apply, verify, and land whatever healed by following `references/heal.md` from the same extracted path.

Both reference docs are self-contained: what to run, how to interpret the output, and exactly when to pause and ask before continuing. Never commit or open a pull request without asking first, no matter how clean a run was.

This file is generic on purpose — it's read by whichever coding agent is active in this workspace (Cursor, Copilot, Antigravity, Gemini CLI, Windsurf, or another AGENTS.md-compatible tool). Claude Code and Kiro have their own richer, conditionally-loaded copy of the same instructions under `.claude/skills/tamash-selenium/` / `.kiro/skills/tamash-selenium/` — if you're one of those two, prefer that copy over this file.
