# Cash-flow Report Screen (zero-ui) — Design

## Goal

Rebuild the **Cash-flow report** screen from the Analytics Exploration design
(`CashflowReport`, artboard *"Cash-flow report · rebuilt: trend + income + vs prior"*) as a
real Compose screen in `zero-ui`. It is a **debug-only preview** reached from
Settings → Developer, exactly like the existing **Charts** gallery — no real-data wiring yet
("use the dev section as the entry point for now"). It composes the existing chart library
(#344) and theme tokens; nothing new is added to the design system.

Design source: `ui_kits/zero/Analytics Exploration.html` → `analytics-screens.jsx`
(`CashflowReport`, lines 640–774) + `analytics-kit.jsx` (`FLOW`, formatters, `SparkLine`).
Dark-only, like the rest of the app.

## What the screen shows (top → bottom)

1. **Header** — `DetailTopBar(title = "Cash flow")` with a back arrow.
2. **Period chip** — a static "Last 6 months" pill, right-aligned (display-only; no picker).
3. **Hero card** (navy `chartHeroSurface`) — "NET CASH FLOW" label + big signed net total, then
   three sub-tiles: **In** (green), **Out** (orange), **Saved** (white %).
4. **By month** card — grouped in/out bars (6 months) via `BarChart`, a header caption showing the
   latest month's in/out, and a "Tap a month for its breakdown" footer (static; no tap target).
5. **Savings rate** card — a `LineChart` sparkline of the monthly savings-rate %, with the average
   and the min–max range, plus month labels under the line.
6. **Income sources** — three rows (Salary / Freelance / Interest), each a tinted
   `CategoryIconView` badge + name + amount + a share progress bar + "N% of income".
7. **Vs previous 6 months** — three comparison rows (Money in / Money out / Savings rate), each
   with the current value and a signed delta chip (green up / red down).

## Architecture — render-only view over a precomputed model

The screen carries non-trivial arithmetic (net, savings rate, a per-month savings-rate series,
income shares, period-over-period deltas). Per the project's **no-calculations-in-views** rule,
all of that lives in a **pure, non-composable factory**; the composable only assigns theme colors,
formats currency/percentages, and lays out.

### `CashflowReportData.kt` (new, package `com.hluhovskyi.zero.ui.chart`)
- Raw mock constants (numbers straight from the design): reuse `ChartMockData.flow6` for the
  6-month in/out series; add `incomeSources` (name + amount + swatch `Color`) and `priorPeriod`
  (prior in / out / savings-rate) — colocated with the other "numbers from the design".
- Immutable UI model emitted by the factory:
  - `CashflowReport(net, totalIn, totalOut, savingsRate, months, latest, savingsTrend,
    savingsRateMin, savingsRateMax, incomeSources, comparisons)`
  - `MonthlyFlow(label, moneyIn, moneyOut)`, `IncomeShare(name, amount, sharePercent, color)`,
    `PeriodComparison(label, nowValue, delta, isMoney)`.
- `fun cashflowReport(): CashflowReport` does **every** calculation: `net = Σin − Σout`,
  `savingsRate = round(net / Σin · 100)`, per-month `savingsTrend = round((in−out)/in · 100)`,
  income `sharePercent = round(amount / Σin · 100)`, comparison `delta = now − was`. No
  arithmetic leaks into the composable. (Mirrors the calc-in-VM rule applied to the Analytics
  hub — here the factory is the projection.)

### `CashflowReportScreen.kt` (new, same package)
- `@Composable fun CashflowReportScreen(onBack: () -> Unit)` — collects `cashflowReport()` once,
  then renders. Private section composables (hero, by-month, savings, income, comparison) read
  the precomputed fields and only **format + render** (currency via a small local formatter
  mirroring the design's `fmt0`/`fmtSign`; color by `delta >= 0`).
- Builds `BarChartData`/`LineChartData` from the precomputed numeric series at render time and
  assigns theme colors — the same number→colored-chart mapping the gallery's `flowBars` helper
  already does (presentation, not calculation).
- Dev-facing labels stay inline with `@file:Suppress("HardcodedComposableString")`, exactly like
  `ChartsGalleryScreen` (these strings never ship to users).

## Reuse — nothing new in the design system

- `BarChart`, `LineChart`, `CategoryIconView`, `DetailTopBar` — all exist.
- Tokens `chartCashIn` / `chartCashOut` / `chartHeroSurface` / `chartHeroContent` /
  `chartHeroContentDim` were pre-seeded by #344 for exactly this hero card. No `ZeroExtraColors`
  changes. Income swatch colors are entity colors (not theme roles) so the mock-data file carries
  a `@Suppress("ZeroThemeBypass")` like `ChartMockData`.

## Wiring (thin) — mirror the Charts entry exactly

- `Destinations.Dev.CashFlow` — new `object CashFlow : Dev` (route `dev/cashflow`) beside
  `Dev.Charts`.
- Settings: `SettingsViewModel.Action.OpenDevCashFlow` + `OnDevCashFlowSelectedHandler` (fun
  interface, `Noop` default) on `SettingsComponent` via `@BindsInstance`, mirroring
  `OnDevChartsSelectedHandler`. `DefaultSettingsViewModel` handles the action → handler.
  `SettingsViewProvider` adds a second `MoreRow` ("Cash flow") in the existing Developer section
  (only when `showDeveloperOptions`).
- App: `MainActivityScreenComponent` settings entry adds
  `.onDevCashFlowSelectedHandler { navigator.navigateTo(Destinations.Dev.CashFlow) }`; register a
  `composable(Destinations.Dev.CashFlow) { CashflowReportScreen(onBack = { navigator.back() }) }`,
  using `devChartsNavigationEntry` as the structural reference.
- New strings: `settings_dev_cashflow`, `settings_dev_cashflow_description`.

## Scope

**In:** the screen + its data factory, the income/prior mock data, two dev-section strings, one
nav destination + settings handler + wiring.

**Out:** real-data wiring, a working period picker, per-month tap-through / drill-down, the
category-merge and scoped-spending screens from the same exploration, animations beyond the chart
library's existing grow/draw-in, light-mode tuning (app is dark-only).

## Testing & verification

- **Unit (JVM):** `cashflowReport()` is the only logic worth testing — assert net, savings rate,
  the per-month savings-rate series, income shares, and the comparison deltas against the fixed
  mock data. New `CashflowReportTest.kt` beside `ChartMathTest.kt`.
- **Lint:** `./gradlew spotlessApply lintDebug` clean — confirms no `ZeroThemeBypass` violations.
- **UI:** build debug, navigate Settings → Developer → Cash flow on the emulator, verify each
  section renders (hero totals, bars, sparkline, income rows, comparison deltas) via
  `android-ui-inspector`. Not done until the inspector confirms it on device.
</content>
</invoke>
