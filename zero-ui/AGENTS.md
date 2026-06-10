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
- **Design tokens**: Color palette, typography scale, corner radii
- **Charts**: `chart/` — `LineChart`, `SignedLineChart`, `BarChart`, `DonutChart` + data models. See [Charts](#charts).

## Charts

Canvas/layout chart primitives in `com.hluhovskyi.zero.ui.chart`, **color- and data-agnostic** —
they take a `Color` and a primitive data class (`LineChartData`, `BarChartData`, `DonutChartData`),
so a feature ViewModel builds the data and passes it in. Built for the Insights feature; not yet
wired to real data.

- **Composables**: `LineChart` (gradient-area sparkline), `SignedLineChart` (net-worth area with a
  dashed zero baseline — red below / green above, area anchored to zero), `BarChart` (grouped cash
  in/out or single series; adaptive bar width; `BarGroup.topLabel` + `barWidth`/`barCornerRadius`
  cover the single-series category trend with value labels + a highlighted current period),
  `DonutChart` (multi-segment ring with a center `content` slot).
- **Each chart degrades on its own terms — never a blank box or a misleading flat line.** Keep this
  when adding states: empty line/bar → dashed baseline; empty donut → hollow dashed ring; a missing
  bar bucket → dashed placeholder; an all-zero bar series → faint baseline tracks; a single point →
  a lone dot (no line).
- **Chart accent colors are tokens on `ZeroExtraColors`** (`chartCashIn`, `chartCashOut`,
  `chartHeroSurface`, …) — same reason as rule 5; don't hardcode chart hex. Per-bar/segment
  (category/entity) colors are the caller's to pass in.
- **Keep the math in `ChartMath.kt`, the drawing thin** — normalization, adaptive widths, and the
  signed (zero-inclusive) scale are pure functions covered by `ChartMathTest`; the composables just
  draw. Add a test there before reaching for new geometry.
- The debug gallery (`ChartsGalleryScreen`, reached via **Settings → Developer → Charts** in debug
  builds) is the live reference for every chart × state. Its dev-only labels file-suppress
  `HardcodedComposableString` rather than bloating the localized string table.

## Adding a Shared Component

1. If a second feature needs a component that lives in a feature package, move it here — never add a cross-feature import inside `zero-core`. Strip domain types via generics or primitives before moving.
2. Components here must not reference any domain types — keep them dumb (accept primitives, strings, Compose types)
3. Keep components stateless — accept data via parameters, emit events via callbacks
