# zero-ui — Agent Guide

Android library module. Design system and dumb reusable Compose components.

## Rules

1. **No dependencies on other zero-* modules** — this is a collection of dumb views. No domain types, no business logic.
2. **`@Composable` interface methods must be abstract** — no default body. Kotlin `DefaultImpls` dispatch bug causes the interface body to run instead of the class override. See [Kotlin / Compose Gotchas](../docs/agents/kotlin-compose-gotchas.md).
3. **`ComposeColor` from hex: use `.hex.toInt()`** — `ComposeColor(ULong)` encodes colorspace bits in lower 6 bits, producing wrong colors.
4. **New composables import `androidx.compose.material3`, never `androidx.compose.material`** — the app migrated to Material 3 (PR #295); M2 imports compile only until they reach a migrated module, then force a hand-migration (incl. signature changes like `DropdownMenuItem(text = …)`) at merge time. The icon packs (`androidx.compose.material.icons.*`) are the one exception — they stay under `material.icons`. Enforced by the `MaterialTwoImport` lint (the bottom-sheet navigator island in `app` is allowlisted in the detector).
5. **Color comes from `ZeroTheme.colors.*`, not `MaterialTheme.colors`/`.colorScheme`** — M3 dropped `MaterialTheme.colors`; `ZeroTheme` is the project's single color source of truth.

## What Lives Here

- **Theme**: `theme/Theme.kt`, `theme/ZeroColors.kt`, `theme/Type.kt`, `theme/Shape.kt` — Material 3 theme setup (`Color.kt` consts are deleted on purpose; see [ColorScheme](../docs/agents/color-scheme.md))
- **Shared components**: Reusable atomic/molecular Compose components (e.g., `AmountDisplay`)
- **Charts**: `ui/chart/` — line, signed-line, bar, donut + `ChartsGalleryScreen` (dev-only gallery)
- **Design tokens**: Color palette, typography scale, corner radii

## Adding a Shared Component

1. If a second feature needs a component that lives in a feature package, move it here — never add a cross-feature import inside `zero-core`. Strip domain types via generics or primitives before moving.
2. Components here must not reference any domain types — keep them dumb (accept primitives, strings, Compose types)
3. Keep components stateless — accept data via parameters, emit events via callbacks
