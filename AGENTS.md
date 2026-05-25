# Zero — Codebase Guide for AI Agents

Zero is a personal finance Android app (Kotlin, Jetpack Compose, Dagger, Room). Tracks expenses, income, and transfers across accounts with multi-currency support.

## Maintaining These Docs

If you discover a non-obvious gotcha, a new pattern, or a rule that isn't documented — update the relevant doc file. Don't document things the code already says clearly. Only document the "why" and the traps.

**Repo-First Documentation**: All plans and architectural docs MUST be saved directly to the repository (e.g. `docs/superpowers/plans/`). Never leave artifacts in temporary tool directories. **Commit the plan doc on the feature branch before starting implementation** — an untracked plan is a lost plan.

**Doc-With-Code**: When implementing changes that affect a domain type, invariant, or pattern already described in `docs/agents/`, update the relevant doc file in the **same commit** as the code change — never defer doc updates to a retro or a follow-up PR.

## Starting New Work

Use the `/lets-do` skill (invoke via `Skill` tool as `zero-project:lets-do`) to kick off any
feature, bug fix, or refactor. It orchestrates the full workflow automatically:

1. **Worktree isolation** — creates a branch + worktree via `superpowers:using-git-worktrees`;
   never lets you touch master
2. **Brainstorming** — `superpowers:brainstorming` (skipped for tiny fixes or with `--no-questions`)
3. **Planning** — `superpowers:writing-plans` for anything >~100 LOC
4. **Execution** — `superpowers:subagent-driven-development`
5. **Verification** — tests + lint + `zero-project:android-ui-inspector` if UI is touched
6. **PR** — opens a GitHub PR as the final deliverable

Pass `--no-questions` to skip brainstorming and proceed straight to execution.

## Cross-Cutting Rules

1. **Branch Isolation** — Every distinct task MUST have a dedicated branch. Never commit directly to `master`. Verify current branch before every commit. See [Branch Management](docs/agents/branch-management.md).
2. **Follow code style conventions** — see [Code Style](docs/agents/code-style.md).
3. **Strict Development Lifecycle**:
    - **Strict Handshake**: No execution or verification (build/test) until the final plan in `docs/superpowers/plans/` is explicitly approved (e.g. "Go ahead").
    - **Plan Verification Steps**: When writing an implementation plan, the verification phase MUST explicitly include steps to run linters (e.g., `./gradlew lintDebug`) and verify UI behavior (via `android-ui-inspector` or `./scripts/ui/dump-ui.sh`). Never write a plan that relies solely on compilation (`assembleDebug`) as its success metric.
    - **Implicit Denial**: Technical feedback is NOT approval. Re-propose and wait for a fresh handshake after any plan update.
    - **Minimalism**: Change ONLY what is in the approved plan. No unrelated refactors, visibility changes, or "cleanup".
    - **Zero Deviation**: Approved plans are binding. Stop and re-propose if implementation requires any architectural or logic change.
    - See [Execution Workflow](docs/agents/execution-workflow.md).
4. **Shared Agent Skills** — Skills live in `skills/` and are symlinked into `.claude/plugins/zero-project/skills/` to keep a single source of truth. Edit via the real path, not the symlink — see [Skills](docs/agents/skills.md).
5. **UI Validation** — Compilation is not validation for UI/layout bugs. Use the `android-ui-inspector` skill (`./scripts/ui/dump-ui.sh`) to empirically verify bounds and visibility via ADB before committing. A UI task is not complete until the inspector confirms it on device.
6. **Library Updates Over Hacks** — Before implementing any complex workaround, check if a minor version bump of relevant project libraries provides a native API that solves the problem.
7. **When invoking brainstorming or writing-plans** — read [Superpowers Workflow](docs/agents/superpowers-workflow.md) first for project-specific optimizations that keep plans lean and design docs focused.
8. **Dependencies live in the version catalog** — build scripts are Kotlin DSL (`*.gradle.kts`) and all versions/libraries/plugins are declared in `gradle/libs.versions.toml`. Add new deps there and reference via `libs.*`; never inline coordinate strings in a module.

## Module Map

```
app                  → Activity, Navigation, screen wiring
zero-core            → ViewModels, UseCases, Dagger Components, ViewProviders
zero-ui              → Design System, shared Compose components
zero-api             → Domain interfaces and types (pure Kotlin)
zero-database        → Room DAOs, Entities, Repository implementations
zero-image-loading   → ImageLoader interface + Coil impl
zero-crash           → CrashComponent (Sentry crash reporting, attach-only)
zero-zenmoney        → ZenMoney CSV import
zero-sync            → JSON export/import and LWW delta sync engine (pure Kotlin JVM, no Android)
zero-remote          → Server-side HTTP calls
```

**Dependency flow:** `app → zero-core → zero-api`, `app → zero-database → zero-api`, `app → zero-sync → zero-api`, `app → zero-remote → zero-api`, `zero-core → zero-ui` (dumb views, no domain types), `zero-core → zero-image-loading`.

Each module has its own `AGENTS.md` with module-specific rules.

**New module: add a per-module `.gitignore` with `/build`.** The root `.gitignore` only matches root `/build`; without this, Gradle artifacts land in your next commit.

## Architecture Quick Reference

Every feature follows: **Component → ViewModel → ViewProvider** (+ optional UseCase).

- **Component** (`@dagger.Component`): DI wiring. Has `Dependencies` interface + `Builder : Buildable<T>`. See [DI Migration](docs/agents/di-migration.md) for the experimental manual alternative.
- **ViewModel** (`: AttachableActionStateModel<Action, State>`): Reactive state via `Flow<State>`, actions via `perform(Action)`, lifecycle via `attach() → Closeable`.
- **ViewProvider** (`: ViewProvider`): Composable that collects state and dispatches actions.
- **Handlers** (`fun interface OnXxxHandler`): Callbacks for screen-to-screen communication, passed via `@BindsInstance`.

See [Architecture Patterns](docs/agents/architecture.md) for full details with code examples.

## Reference Docs

- [Architecture Patterns](docs/agents/architecture.md) — Component/ViewModel/UseCase/ViewProvider, attach() lifecycle, Flow composition
- [Concurrency](docs/agents/concurrency.md) — Async-first design, Dispatchers, Flow vs suspend, state management, pagination
- [Data Layer](docs/agents/data-layer.md) — Repository pattern, Room entities, query/insert conventions
- [Backup](docs/agents/backup.md) — Android Auto Backup (allowlist rules, what's backed up, how to change it) + Drive backup pointer
- [Navigation](docs/agents/navigation.md) — URL-based routing, Destination, Argument, Navigator, returning results between screens
- [Dependency Injection](docs/agents/dependency-injection.md) — Dagger component structure, @BindsInstance, lifecycle timing
- [DI Migration](docs/agents/di-migration.md) — Experimental: manual DI without Dagger for KMP readiness. Alternatives, migration pattern, checklist, order
- [Module Boundaries](docs/agents/module-boundaries.md) — Module dependency rules
- [ImageLoader](docs/agents/image-loading.md) — Interface design, tint handling
- [ColorScheme](docs/agents/color-scheme.md) — ColorScheme, ColorValue, colorRepository
- [Testing](docs/agents/testing.md) — JUnit/Mockito, Flow type inference, coroutines testing
- [E2E / Instrumented tests](app/src/androidTest/AGENTS.md) — Robots, Orchestrator, flake-handling rule
- [Kotlin / Compose Gotchas](docs/agents/kotlin-compose-gotchas.md) — DefaultImpls dispatch bug, ComposeColor pitfall
- [Branch Management](docs/agents/branch-management.md) — Protected master, PR workflow
- [Code Style](docs/agents/code-style.md) — Conventions to keep code consistent across the codebase
- [Execution Workflow](docs/agents/execution-workflow.md) — Design-first, UI verification loop, complexity circuit breaker
- [Superpowers Workflow](docs/agents/superpowers-workflow.md) — Project-specific rules for keeping plans lean and design docs focused
- [Skills](docs/agents/skills.md) — Adding skills, plugin loader setup, troubleshooting
- [Feedback Infra](docs/agents/feedback-infra.md) — Play Integrity-gated feedback pipe (zero-remote module, cloud function, secrets matrix)
