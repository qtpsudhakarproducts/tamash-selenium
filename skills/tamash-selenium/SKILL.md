---
name: tamash-selenium
description: Set up and run tamash-selenium's self-healing Selenium Java workflow locally — onboard a Maven/Gradle project to its standards, then review, apply, verify, and land runtime heals as permanent fixes.
allowed-tools: Bash(mvn:*) Bash(./mvnw:*) Bash(mvnw:*) Bash(sh:*) Bash(git:*) Bash(gh:*) Read Edit Write Grep Glob
---

# tamash-selenium — local self-healing workflow

`tamash-selenium` is a self-healing add-on for Selenium Java: when `findElement` can't find its element, it asks an AI model (or a free rule-based matcher) to locate the element on the live page and retries the call at runtime, right when the failure happens. This skill is the **local counterpart to its CI automation** (run → `apply-heals` → verify → PR → report) — the same loop, but with a real agent reasoning about each fix instead of a static PR diff nobody reads closely.

## Start here: run `doctor`

Everything below branches on one command — run it before assuming which path applies:

```bash
mvn -q exec:java -Dexec.args="doctor"
```

(Use `./mvnw` if the project has a wrapper. For a multi-module build, run it from the module that has the `tamash-selenium` dependency.)

- **Any `[FAIL]` or `[WARN]`** (provider misconfigured, brittle CSS/XPath locators with no descriptive name), **or** the driver isn't actually wrapped with `SelfHealingDriver.wrap(...)` / a `tamash-selenium` integration (doctor can't see this — you have to check) → this project hasn't adopted tamash-selenium's standards yet. Follow **[references/onboarding.md](references/onboarding.md)** first.
- **No `[FAIL]`/`[WARN]` remaining**, and the driver is wrapped → the project's ready, regardless of what else is showing. An `[INFO]` row (Vision Fallback capability, provider defaulting to `tamash`, an inline-locator count) is never a blocker — those are observational, not a standard to meet. Follow **[references/heal.md](references/heal.md)** — its own first step is running the suite, so this applies whether or not it's already been run; you don't need pre-existing heals to justify starting it.

Don't guess which one applies from what the user says or how the project looks — `doctor`'s actual output plus a quick check that the driver is wrapped is the source of truth every time.

## What this skill does NOT do

- It never invents a healing strategy of its own. Every action here is one of tamash-selenium's own existing commands (`mvn exec:java -Dexec.args="doctor"`, `... "apply-heals"`, the generated `verify-heals.sh` / `.cmd`) — this skill is orchestration and judgment layered on commands that already exist, not a new capability bolted onto the package.
- It never commits or opens a PR without asking first, no matter how clean a run was — see the LAND step in [heal.md](references/heal.md).
- It never touches anything `apply-heals` itself already excludes (one-shot ref/vision heals with no durable selector, action-recovery-only heals, assert-absent / `wait.until(...)`-context exclusions) — if `apply-heals` didn't offer to change it, this skill doesn't either.

## Getting this skill into a project

No coding agent auto-discovers a skill bundled inside a JAR in the local Maven cache — this is always a real, explicit one-time step after adding the `tamash-selenium` dependency, never automatic. The skill files ship inside the `tamash-selenium` JAR under `skills/tamash-selenium/`.

**Claude Code** — extract straight from the dependency JAR:

```bash
mvn -q dependency:unpack \
  -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.2 \
  -Dmdep.unpack.includes="skills/**" \
  -DoutputDirectory=.claude
# → .claude/skills/tamash-selenium/SKILL.md
```

**Kiro** — identical `SKILL.md` format, different root directory (`-DoutputDirectory=.kiro`):

```bash
mvn -q dependency:unpack -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.2 \
  -Dmdep.unpack.includes="skills/**" -DoutputDirectory=.kiro
```

**Cursor** — its own rule format (`.mdc`, with frontmatter Cursor uses to decide when to attach it). Unpack to a temp dir and copy the adapter:

```bash
mvn -q dependency:unpack -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.2 \
  -Dmdep.unpack.includes="skills/**" -DoutputDirectory=target/tamash-skill
mkdir -p .cursor/rules
cp target/tamash-skill/skills/tamash-selenium/adapters/cursor-tamash-selenium.mdc .cursor/rules/
```

**GitHub Copilot** — append (or create) its single instructions file:

```bash
mvn -q dependency:unpack -Dartifact=com.vibetestq.qtpsudhakar:tamash-selenium:0.1.0-beta.2 \
  -Dmdep.unpack.includes="skills/**" -DoutputDirectory=target/tamash-skill
mkdir -p .github
cat target/tamash-skill/skills/tamash-selenium/adapters/copilot-instructions-tamash-selenium.md >> .github/copilot-instructions.md
```

**Antigravity, Gemini CLI, Windsurf, Zed, Aider, and other `AGENTS.md`-reading tools** — one file covers all of them:

```bash
cp target/tamash-skill/skills/tamash-selenium/adapters/AGENTS.md ./AGENTS.md
# or, if AGENTS.md already exists, append its content instead of overwriting
```

If the project is a git checkout of `tamash-selenium` itself, the files are already at `skills/tamash-selenium/` — copy them directly, no `dependency:unpack` needed. Any of the copy targets above (`.claude/skills/`, `.kiro/skills/`) can also be done once at the user level (`~/.claude/skills/tamash-selenium`, `~/.kiro/skills/tamash-selenium`) to cover every project on the machine instead of one at a time.
