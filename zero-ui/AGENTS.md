# zero-ui — Agent Guide

Android library module. Design system and shared Compose components.

## Rules

1. **Cannot depend on `zero-core`** — only depends on `zero-api`. This prevents circular dependencies.
2. **No business logic** — only UI components, theme definitions, and presentation helpers.
3. **`@Composable` interface methods must be abstract** — no default body. Kotlin `DefaultImpls` dispatch bug causes the interface body to run instead of the class override. See [Kotlin / Compose Gotchas](../docs/agents/kotlin-compose-gotchas.md).
4. **`ComposeColor` from hex: use `.hex.toInt()`** — `ComposeColor(ULong)` encodes colorspace bits in lower 6 bits, producing wrong colors.

## What Lives Here

- **Theme**: `Theme.kt`, `Color.kt`, `Type.kt`, `Shape.kt` — Material 3 theme setup
- **Shared components**: Reusable atomic/molecular Compose components (e.g., `CategoryIconView`, `AmountDisplay`)
- **Design tokens**: Color palette, typography scale, corner radii

## Adding a Shared Component

1. Check if it's truly reusable across features — if only one feature uses it, keep it in `zero-core`
2. Only reference types from `zero-api` (e.g., `ColorScheme`, `Amount`, `Image`) — never types from `zero-core`
3. Keep components stateless — accept data via parameters, emit events via callbacks
