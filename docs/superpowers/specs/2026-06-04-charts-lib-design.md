# Charts Library (zero-ui) — Design

## Goal

Add a small, reusable chart-rendering library to `zero-ui` covering the three chart
shapes from the Analytics Exploration design — **line**, **bars**, **donut** — plus a
debug-only gallery screen (Settings → Developer → Charts) that exercises each chart
across data states (none / single / 6-month / 12-month). Charts are **not** wired to
real data; the gallery uses mock data taken from the design. The composables are built
color- and data-agnostic so the future Insights feature can drive them with real domain
data.

Design source: `ui_kits/zero/Analytics Exploration.html` (+ `analytics-kit.jsx`,
`edgecases.jsx`). Revised design adds `BarEmptyStates` to `edgecases.jsx` (richer no-data
variants). Renders dark by default; the app is dark-only today but tokens carry light values too.

## Why Canvas (not a charting dependency)

The three shapes are bespoke and simple: a gradient sparkline, paired vertical bars with
adaptive width, and a stroke-dash segmented ring. The codebase already draws a progress
donut with `androidx.compose.foundation.Canvas` in `budget/SummaryBar.kt` using the exact
accent palette (`#1A2E52`, `#5DDBA8`). Canvas gives pixel-faithful control with no new
dependency and matches precedent. A third-party lib (Vico/MPAndroidChart) would be heavier
and fight the design.

## Scope

In: chart composables + data models, chart accent tokens, mock-data provider, gallery
screen, Settings "Developer" section (debug only), one nav destination + wiring.

Out: real-data wiring, Insights feature, period pickers, category breakdown rows, the
navy hero card chrome beyond what the gallery needs to showcase the bar chart, animations
beyond a single grow/draw-in on first composition.

## Components (all new, in `zero-ui` package `com.hluhovskyi.zero.ui.chart`)

### Data models — `ChartData.kt`
- `LineChartData(points: List<Float>, labels: List<String> = emptyList())` — one series.
- `BarChartData(groups: List<BarGroup>)`, `BarGroup(label: String, bars: List<BarValue>)`,
  `BarValue(value: Float, color: Color)`. Grouped (in/out) = 2 bars per group; single-series
  = 1 bar per group.
- `DonutChartData(segments: List<DonutSegment>)`, `DonutSegment(value: Float, color: Color)`.

All carry only primitives + `Color` — no domain types (zero-ui rule).

### `LineChart` — `LineChart.kt`
Canvas sparkline. Min/max normalized polyline with `preserveAspectRatio:none`-style
non-uniform fit, gradient area fill (alpha 0.28 → 0), end-point dot. Params: `data`,
`lineColor`, `modifier`, `strokeWidth`, `showArea`, `showEndpoint`.
Degenerate handling — **the real engineering**: empty → draw nothing (caller shows empty
state); 1 point → dot only, no line; `min == max` (flat) → centered horizontal line.

### `BarChart` — `BarChart.kt`
Layout vertical bars, grouped or single. Adaptive bar width by bucket count
(per `edgecases.jsx`: n≤6→12dp, ≤9→10, ≤12→8, ≤16→5, else 3; inner gap 3→1; labels hidden
when n>14). Min bar height 2dp so small values still register. Params: `data`, `modifier`,
`barAreaHeight`, `showLabels`, `emptyBarColor`, `placeholderColor`.

**Empty/sparse states — the updated design's core principle: "keep the chart frame; never show
a blank box or a misleading flat line."** The primitive handles this so callers (gallery, future
Insights) compose only the messaging:
- **Empty bucket** (`BarGroup.bars` empty) → a **dashed baseline placeholder** in that slot
  ("no data yet for this bucket"). Drives the *partial history* state (real bars + dashed slots).
- **All-zero series** (no positive value anywhere) → every bar renders as a **faint solid baseline
  track** instead of a colored stub. Drives the *zero-in-period* state. A single zero among real
  values still shows the normal 2dp colored stub.
- **No groups at all** → nothing (the caller's card frames it; see the *no-history* ghost example).

The three named design states (`BarsNoHistory`, `BarsPartial`, `BarsZeroPeriod`) are reproduced in
the gallery by composing this primitive with overlays (ghost bars at 18% alpha + pill; dashed slots;
"$0 in / $0 out" message) — the copy is feature-specific so it stays out of the primitive.

### `DonutChart` — `DonutChart.kt`
Canvas multi-segment ring over a full track. `drawArc` per segment, start −90°, swept by
`value/total`, round-capped, with a small gap. Center content via a `content: @Composable
BoxScope.() -> Unit` slot. Params: `data`, `modifier`, `strokeWidth`, `trackColor`, `content`.
Degenerate: total 0 / empty → track ring only (so an empty-state label can sit in center);
1 segment → full ring.

### Accent tokens — extend `ZeroExtraColors` (theme package, lint-clean)
Add: `chartCashIn` (green `#5DDBA8`), `chartCashOut` (orange `#EBA07C`), `chartHeroSurface`
(navy `#1A2E52`), `chartHeroContent`/`chartHeroContentDim` (white / white-alpha for text on
navy), `chartDonutTrack` (= surfaceContainer per theme). Light = dark for the vivid accents
(they sit on the navy card in the design); surface/track follow the palette. Exposed via
`ZeroTheme.colors.*`. Avoids `@Suppress("ZeroThemeBypass")` and gives Insights a single source.

### Mock data — `ChartMockData.kt` (gallery-only)
6-month FLOW (in/out), 12-month series (from `MONTHLY24` tail), weekly (1-month), net-worth
line, donut category segments — values straight from the design. Category swatch hexes are
entity colors, not theme tokens, so this file carries a file-level `@Suppress("ZeroThemeBypass")`.

### Gallery — `ChartsGalleryScreen.kt`
Scrollable screen, `DetailTopBar` with back, sections per chart type. States:
- **Line:** no data / 1 point / 6 months / 12 months (net-worth series).
- **Bars:** 1 month→weekly / 6 months / 12 months, plus the three empty/sparse states
  (no-history ghost+pill, partial real+dashed, zero-period baseline+"$0 in/$0 out"), plus a navy
  `chartHeroSurface` FlowCard showcase.
- **Donut:** none / 1 / 3 / all categories.

Param: `onBack: () -> Unit`. Pure dumb view.

## Wiring (thin)

- `Destinations.Dev.Charts` — new `sealed interface Dev` + `object Charts` (route `dev/charts`).
- `OnDevChartsSelectedHandler` (fun interface, `Noop` default) on `SettingsComponent` via
  `@BindsInstance`, mirroring `OnBackupSelectedHandler`.
- `SettingsComponent` gains `@BindsInstance isDebugBuild: Boolean`; `SettingsViewModel.State`
  gains `showDeveloperOptions`; `SettingsViewProvider` renders a "DEVELOPER" `MoreSection`
  with a "Charts" `MoreRow` only when true. Action `OpenDevCharts` → handler.
- App: `MainActivityScreenComponent` settings entry passes `BuildConfig.DEBUG` +
  `.onDevChartsSelectedHandler { navigator.navigateTo(Destinations.Dev.Charts) }`; register
  `composable(Destinations.Dev.Charts) { ChartsGalleryScreen(onBack = { navigator.back() }) }`.
  Use `accountNavigationEntry`/`settingsNavigationEntry` as structural references.

## Testing & verification

- Unit: a small `ChartMath`-style helper for line normalization + adaptive bar width is the
  only logic worth a JVM test (degenerate cases: empty, single, flat). Keep drawing untested.
- Lint: `./gradlew lintDebug` must pass — confirms no `ZeroThemeBypass` violations.
- UI: build debug, navigate Settings → Developer → Charts on emulator, screenshot each state
  via `android-ui-inspector`. A chart is not done until the inspector confirms it renders.
</content>
</invoke>
