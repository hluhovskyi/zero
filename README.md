# Zero

<p>
  <img src="docs/screenshots/transactions.png" height="520">
  <img src="docs/screenshots/accounts.png" height="520">
</p>

**A personal finance Android app.** Track expenses, income, and transfers across accounts in any currency. Multi-account, multi-currency, fast.

## Features

- **Multi-currency** accounts and transfers — record amounts at the rate you saw
- **Per-category** monthly stats: amount, count, share of total
- **ZenMoney CSV** import — bring your history with you
- **Local-first sync** — JSON export/import with LWW delta semantics; merge devices without a server
- **Real-time search** across accounts and categories

## Tech

Kotlin · Jetpack Compose · Room · Dagger 2 · Coroutines + Flow · kotlinx-serialization

7 modules with enforced dependency boundaries. `zero-sync` is a pure Kotlin JVM LWW delta engine — versioned JSON format, backward-compatible fixture tests, and a lint rule that fails the build if any serialized field is missing `@SerialName`.

## Build

```bash
./gradlew installDebug          # build + install to a connected device
./gradlew testDebugUnitTest     # unit tests
./gradlew lintDebug             # lint + custom rules
```

Requires Android SDK 34, JDK 21.

## How it's built

Every feature ships through the same pipeline: **brainstorm → spec → plan-on-disk → implement → verify → PR.** The plumbing lives in this repo:

- [`AGENTS.md`](AGENTS.md) at every module — non-obvious invariants, gotchas, conventions
- [`docs/agents/`](docs/agents/) — architecture, concurrency, navigation, DI, testing
- [`docs/superpowers/plans/`](docs/superpowers/plans/) — every implementation plan, committed before any code is written
- [`skills/`](skills/) — custom workflow automation:

| Skill | What it does |
|---|---|
| `lets-do` | One command for the full SDLC — worktree → brainstorm → plan → implement → verify → PR |
| `android-ui-inspector` | ADB + uiautomator: read the live layout tree, because "it compiled" isn't validation |
| `fetch-design` | Pulls design assets and specs before the first line of Compose code |
| `scaffold-feature` | Generates Component / ViewModel / ViewProvider / Handlers stubs so plans focus on logic |
| `pr-merge` | Tests + lint + build, squash-merge, polls CI, cleans the branch |
| `pr-address` | Walks PR review comments one by one; opens issues for deferred work |
| `retro` | Post-feature retrospective; updates `AGENTS.md` so the next session starts smarter |

A handful of patterns keep the wheels on as the codebase grows:

- **Per-module `AGENTS.md`** — invariants live next to the code they constrain, not in one giant root doc
- **Plans on disk** — an untracked plan is a lost plan; every feature has a committed spec under `docs/superpowers/plans/`
- **Lint as guardrail** — custom rules catch hardcoded Compose strings, uppercase string-resources, and serialization fields missing `@SerialName`, so the build fails before a bad PR can ship
- **Worktree + emulator pool** — each working session gets its own git worktree and a pinned emulator, so parallel work doesn't collide (`scripts/emulator/`)
