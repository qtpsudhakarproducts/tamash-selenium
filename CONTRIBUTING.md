# Contributing to tamash-selenium

Thanks for taking a look. This project is Apache-2.0 licensed — fork it, modify it, ship it,
just keep the copyright/license notices intact (see [LICENSE](LICENSE) and [NOTICE](NOTICE)).

## Before you start

- Read [TESTING.md](TESTING.md) — the operating manual for verifying a change. The core rule:
  **verify for real**. Unit tests catch regressions in deterministic code; anything touching a
  live `WebDriver` or a provider call needs a real end-to-end run.
- [RELEASE-TESTING.md](RELEASE-TESTING.md) is the honest, current coverage matrix.
- [TEST-PARITY-ROADMAP.md](TEST-PARITY-ROADMAP.md) tracks open work.

## Making a change

```sh
mvn test               # unit suite — fast, no browser, no AI
mvn test -Pbrowser      # + the rule-based browser suite (real Chrome, no network)
```

Both must be green before opening a PR. If your change touches provider request/response
handling, locator derivation, or `apply-heals`, also run the relevant real-AI test locally
(`AiHealingE2ETest`, `ActionRecoveryTest` — see TESTING.md) and say what you ran in the PR
description; CI only runs the AI suite when the right secret is configured.

## Reporting a bug / requesting a feature

Open a GitHub issue. For a bug, include: the `HEALER_PROVIDER` in use, the console
`[self-healer] …` line if any, and — if you can share it — a minimal `data:text/html,…` or
`page.setContent`-style repro.

## Code style

Match the surrounding file: comment density, naming, and idiom. No unrelated reformatting in a
functional PR.
