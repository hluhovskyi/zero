# Zero — Codebase Guide for AI Agents

Zero is a personal finance Android app (Kotlin, Jetpack Compose, Dagger, Room). Tracks expenses, income, and transfers across accounts with multi-currency support.

## Cross-Cutting Rules

1. **Never commit directly to `master`** — feature branch + PR always. See [Branch Management](docs/agents/branch-management.md).
2. **Follow code style conventions** — see [Code Style](docs/agents/code-style.md).

## Module Map

```
app                  → Activity, Navigation, screen wiring
zero-core            → ViewModels, UseCases, Dagger Components, ViewProviders
zero-ui              → Design System, shared Compose components
zero-api             → Domain interfaces and types (pure Kotlin)
zero-database        → Room DAOs, Entities, Repository implementations
zero-image-loading   → ImageLoader interface + Coil impl
zero-zenmoney        → ZenMoney CSV import
```

**Dependency flow:** `app → zero-core → zero-api`, `app → zero-database → zero-api`, `zero-core → zero-ui` (dumb views, no domain types), `zero-core → zero-image-loading`.

Each module has its own `AGENTS.md` with module-specific rules.

## Architecture Quick Reference

Every feature follows: **Component → ViewModel → ViewProvider** (+ optional UseCase).

- **Component** (`@dagger.Component`): DI wiring. Has `Dependencies` interface + `Builder : Buildable<T>`.
- **ViewModel** (`: AttachableActionStateModel<Action, State>`): Reactive state via `Flow<State>`, actions via `perform(Action)`, lifecycle via `attach() → Closeable`.
- **ViewProvider** (`: ViewProvider`): Composable that collects state and dispatches actions.
- **Handlers** (`fun interface OnXxxHandler`): Callbacks for screen-to-screen communication, passed via `@BindsInstance`.

See [Architecture Patterns](docs/agents/architecture.md) for full details with code examples.

## Reference Docs

- [Architecture Patterns](docs/agents/architecture.md) — Component/ViewModel/UseCase/ViewProvider, attach() lifecycle, Flow composition
- [Data Layer](docs/agents/data-layer.md) — Repository pattern, Room entities, query/insert conventions
- [Navigation](docs/agents/navigation.md) — URL-based routing, Destination, Argument, Navigator, handler pattern
- [Dependency Injection](docs/agents/dependency-injection.md) — Dagger component structure, @BindsInstance, lifecycle timing
- [Module Boundaries](docs/agents/module-boundaries.md) — Module dependency rules
- [ImageLoader](docs/agents/image-loading.md) — Interface design, tint handling
- [ColorScheme](docs/agents/color-scheme.md) — ColorScheme, ColorValue, colorRepository
- [Testing](docs/agents/testing.md) — JUnit/Mockito, Flow type inference, coroutines testing
- [Kotlin / Compose Gotchas](docs/agents/kotlin-compose-gotchas.md) — DefaultImpls dispatch bug, ComposeColor pitfall
- [Branch Management](docs/agents/branch-management.md) — Protected master, PR workflow
- [Code Style](docs/agents/code-style.md) — Conventions to keep code consistent across the codebase
