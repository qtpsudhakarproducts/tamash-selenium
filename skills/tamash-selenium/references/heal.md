# Reviewing, applying, verifying, and landing local heals

The local counterpart to tamash-selenium's CI automation (test → `apply-heals` → verify → PR → report) — same steps, same "never land an unproven fix" discipline, but run by an agent with real judgment in the loop instead of unattended.

Prerequisite: `doctor` reports no `[WARN]`/`[FAIL]` and the driver is wrapped — see [onboarding.md](onboarding.md) if not.

## The loop

Every gate below defaults to **continue** except two — genuine ambiguity in REVIEW, and anything past VERIFY. That's what makes this an actual loop rather than a manual walkthrough: a clean run with unambiguous heals and a passing verify goes start to finish with no interruptions, and the agent only surfaces itself when something genuinely warrants a human's judgment.

### 1. RUN

```bash
mvn test          # or the project's own command; -Dtest=SomeTest for a named subset
```

Runs with healing already enabled — nothing extra to configure once onboarding is done. Every attempt, healed or not, is appended to `.tamash-selenium/heals.jsonl`, and the console prints one line per attempt:

```
[self-healer] src/main/java/com/wd/pom/AddEmployeePage.java:21 — driver.findElement "First Name (textbox)" -> HEALED [provider=tamash, vision=no, actionRecovery=no, suggested="By.name(\"firstName\")"] — no such element: Unable to locate element: {"method":"css selector","selector":"*[name='first_name']"}
```

The console line has everything the REVIEW step needs, but the same detail also lands in the HTML step report if the suite was run with `-DTAMASH_REPORT=target/tamash-report.html` — worth adding to the run if a console line alone doesn't answer a question: per-step timeline, which steps healed (recovered selector, provider, token cost), and the DOM snapshot on an unrecovered failure.

Each `heals.jsonl` line also carries a ready-to-read `newLocator` (`By.name("firstName")`) and `newFindBy` (`@FindBy(name = "firstName")`) alongside the structured `suggestion` — you can eyeball the file directly.

**GATE — did anything heal?**

- No `HEALED` lines at all, or `.tamash-selenium/heals.jsonl` doesn't exist / has no entry with a `suggestion` → **stop here.** Report "suite passed, nothing needed healing" (or "suite failed, but nothing that self-healing can fix — see the failures"). The loop ends — nothing to review, apply, or land.
- At least one `HEALED` line with a durable suggestion → continue to REVIEW.

Also note — but don't act on yet — any line where the action failed and was **not** healed (`stage=ai_declined`, `stage=replay_failed`, etc.), and any `HEALED` line marked `needsReview=yes` where no durable selector could be derived (one-shot ref/vision heal). Those are real, still-broken or still-fragile locators `apply-heals` can't fully fix. Carry them into the final report as things a human needs to look at directly.

### 2. REVIEW

```bash
mvn -q exec:java -Dexec.args="apply-heals --dry-run"
```

Produces a table like:

```
Would fix (2, 1 needing review)
  Location                        Before                        After                          Review
  …/AddEmployeePage.java:21       By.name("first_name")          By.name("firstName")           —
  …/EmployeeIdTest.java:31        By.cssSelector("#emp-id")      By.xpath("(//*[normalize-…])…  ⚠ yes
  ⚠ …/EmployeeIdTest.java:31 — No stable identity of its own — durable selector anchors on nearby text instead.
```

**GATE — is each fix trustworthy?**

- **Review column is `—`** (came from the element's own real identity: id, name, test id, accessible role+name, label, or placeholder → `By.id` / `By.name` / a `By.cssSelector` attribute selector) → high confidence. Continue.
- **Review column is `⚠ yes`** (came from a nearby label or a structural fallback — the warning line under the table explains why; these are the `near` / `adjacent` / `scoped` XPath forms) → actually look closer before deciding. Open the target file, and the real page/component if it helps. Form a genuine opinion, don't just relay the flag.
  - Confident it's correct → continue, but say so explicitly in the final report (don't silently treat it the same as an unflagged fix).
  - Still genuinely unsure → **PAUSE.** Ask the user, naming the specific fix, its location, and exactly why it's uncertain. Don't guess past this point.

Anything in the **Skipped** table (composite `@FindBy`, unsupported strategy, source line no longer matches) is informational — `apply-heals` won't touch it; note it for the report.

### 3. APPLY

```bash
mvn -q exec:java -Dexec.args="apply-heals --yes"
```

**Always pass `--yes` when you're the one running this, not a human at a keyboard.** The confirmation prompt (`Apply N fix(es) to your source files (M needing review)? [y/N]:`) only exists for a real interactive terminal; in a genuinely non-interactive invocation it's skipped anyway, but `--yes` is the documented, supported way to answer it deliberately rather than relying on stdin behavior you haven't verified. Never pipe an answer into stdin (`echo y | ...`).

`apply-heals` rewrites the recorded `By.xxx(...)` call (or `@FindBy(...)` annotation), writes `apply-heals-report.md` / `.json` under `.tamash-selenium/` (timestamped copies under `history/`), generates `.tamash-selenium/verify-heals.sh` / `.cmd`, then archives and clears `heals.jsonl`.

**GATE — did it match expectations?**

- Output reports the same count of fixes as the dry-run showed, `0 skipped` beyond what the dry-run's Skipped table already listed → continue.
- Anything unexpected (fewer applied, extra files skipped, an error) → **stop**, surface exactly what didn't match before doing anything else.

### 4. VERIFY

```bash
sh .tamash-selenium/verify-heals.sh          # Windows: .tamash-selenium\verify-heals.cmd
```

Sets `HEALER_ENABLED=false` and re-runs **only the tests actually affected** (`mvn -q test -DHEALER_ENABLED=false -Dtest='<affected>'`), proving the rewritten selectors work standalone — not just "worked while healing was still there to catch a mistake."

**GATE — hard gate, never soft:**

- Exit code 0, all affected tests green → continue to LAND.
- Any failure → **STOP. Do not land.** Report exactly which test/assertion failed and why. A failed verify means either the applied fix is actually wrong, or something unrelated broke — either way, a human needs to see this before anything is committed. Mirrors the CI workflow's own rule: a bad verification is always surfaced, never silently landed, and never silently discarded either. (`git checkout -- <files>` reverts the applied rewrites if the user wants to start over.)

### 5. LAND

**This gate never auto-continues, regardless of how clean steps 1–4 were.** Present:

- What's ready to land — the exact diff (`git diff`), or point at `.tamash-selenium/apply-heals-report.md`.
- Proof it works — the verify-heals result from step 4.

Then ask the user to choose:

- Commit directly on the current branch.
- Create a new branch and `gh pr create` — compose the PR body the same way the CI workflow does (the before/after report plus the verification result, clearly labeled pass/fail).
- Leave everything staged/unstaged for the user to handle themselves.

Never pick for them, and never commit or push without an explicit answer — same standing rule as any other push/publish action.

### 6. CLOSE THE LOOP

Once landed, re-run the full suite once more (healing still on):

```bash
mvn test
```

A clean pass with **zero new `HEALED` lines** confirms the fix is genuinely durable, not just "verify-heals happened to pass once" — and leaves the project in a clean state for the next time this loop runs.

### 7. REPORT

One summary at the end, regardless of where the loop stopped:

- What ran, what healed, what got applied / verified / landed.
- Anything still needing a human: un-healed failures from step 1, `needsReview` heals with no durable selector, or a fix paused on in step 2.
- Where to look for more detail: `.tamash-selenium/apply-heals-report.md` (or `.json`), `.tamash-selenium/history/` for prior runs, or the HTML step report if `-DTAMASH_REPORT=` was set.
