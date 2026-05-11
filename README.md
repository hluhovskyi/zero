# Zero — Personal Finance Tracker

Personal finance Android app (Kotlin, Jetpack Compose) for tracking expenses, income, and transfers across accounts with multi-currency support. Built as a hands-on experiment in agentic development with Claude Code.

<img src="docs/screenshots/transactions.png" height="500"> <img src="docs/screenshots/accounts.png" height="500">

## Features

- Expense, income, and transfer tracking across accounts in any currency
- Per-category monthly spending stats (amount, count, % of total)
- ZenMoney CSV import; JSON export/import with LWW delta sync
- Real-time search by account or category name

## Development process

Each feature follows a structured agent session: brainstorm → UI design in Claude Design → written spec → implementation plan committed to `docs/superpowers/plans/` → Claude implements task-by-task with lint, tests, and UI verification at the end.

Every module has an `AGENTS.md` with the non-obvious rules and invariants the agent needs across sessions.

## Skills

Custom Claude Code skills in `skills/`:

| Skill | Trigger | Description |
|---|---|---|
| `lets-do` | "let's do X", "implement X" | Full development workflow — worktree isolation, brainstorm, plan, implement, verify (tests + lint + UI), open PR |
| `android-ui-inspector` | debugging UI layouts or bounds | ADB + uiautomator screenshot so the agent verifies actual Compose layout bounds, not just a successful build |
| `fetch-design` | Claude Design URL pasted | Downloads design assets and reads layout specs before writing any Compose code |
| `scaffold-feature` | "add a new screen", "scaffold X" | Generates Component / ViewModel / ViewProvider / Handlers stubs so plans describe business logic, not boilerplate |
| `pr-merge` | "merge the PR", "ship this" | Runs tests + lint + build, merges with squash, polls CI, cleans up branch |
| `pr-address` | "address PR comments" | Pulls review comments and addresses them one by one; supports creating GitHub issues for deferred work |
| `retro` | "what went well", "update the docs" | Post-feature retrospective that surfaces what caused extra iterations and updates `AGENTS.md` so the next session starts smarter |

## Tech

Kotlin · Jetpack Compose · Room · Dagger 2 · Coroutines + Flow · kotlinx-serialization

7 modules with enforced dependency boundaries. `zero-sync` is a pure Kotlin JVM LWW delta sync engine — no Android deps, versioned JSON format, backward-compat fixture tests, and a lint rule that fails the build if any serialized field is missing `@SerialName`.

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

Requires Android SDK 34, JDK 21.
