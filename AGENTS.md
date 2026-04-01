# Zero — Codebase Guide for AI Agents

## Table of Contents

- [Navigation](docs/agents/navigation.md) — URL-based routing, `Destination`, `Argument<Id>`, `Id` semantics, data passing between screens
- [Dependency Injection](docs/agents/dependency-injection.md) — Dagger component structure, `@BindsInstance` conventions, lifecycle timing
- [Module Boundaries](docs/agents/module-boundaries.md) — `app`, `zero-core`, `zero-ui`, `zero-api`, `zero-image-loading` and their rules
- [ImageLoader](docs/agents/image-loading.md) — Interface design, tint via `rememberAsyncImagePainter`, `ComposeColor` int constructor
- [ColorScheme](docs/agents/color-scheme.md) — `ColorScheme`, `ColorScheme.Grey` fallback, `colorRepository.schemeFor`, `ColorValue`
- [Pagination](docs/agents/pagination.md) — Cursor-based loading, "After" reactive query, padding for day summaries
- [Kotlin / Compose Gotchas](docs/agents/kotlin-compose-gotchas.md) — `DefaultImpls` dispatch bug, `ComposeColor` constructor pitfall, type inference
