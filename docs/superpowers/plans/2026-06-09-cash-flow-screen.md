# Cash-flow Report Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the design's **Cash-flow report** as a debug-only Compose screen in `zero-ui`, reached from Settings → Developer (like the Charts gallery), composing the existing chart library.

**Architecture:** A pure non-composable factory (`cashflowReport()`) does all arithmetic and emits an immutable UI model; `CashflowReportScreen` only assigns theme colors, formats, and renders (no-calc-in-views). Thin nav/settings wiring mirrors the existing `Dev.Charts` entry exactly.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Dagger, JUnit. Spec: `docs/superpowers/specs/2026-06-09-cash-flow-screen-design.md`.

---

## File Structure

- `zero-ui/.../ui/chart/CashflowReportData.kt` — **new.** Mock constants (income, prior) + UI model data classes + `cashflowReport()` factory (all arithmetic). Analog: `ChartMockData.kt` (file-level `@Suppress("ZeroThemeBypass")` for swatch colors).
- `zero-ui/.../ui/chart/CashflowReportScreen.kt` — **new.** The render-only composable. Analog: `ChartsGalleryScreen.kt` (same package, `@file:Suppress("HardcodedComposableString")`, `DetailTopBar`, `GalleryCard`-style cards).
- `zero-ui/src/test/.../ui/chart/CashflowReportTest.kt` — **new.** JVM test of the factory. Analog: `ChartMathTest.kt`.
- Wiring (all mirror the `Dev.Charts` precedent 1:1):
  - `app/.../navigation/Destinations.kt:101-103` — add `object CashFlow`.
  - `zero-core/.../settings/OnDevCashFlowSelectedHandler.kt` — **new.** Analog: `OnDevChartsSelectedHandler.kt`.
  - `zero-core/.../settings/SettingsViewModel.kt` — add `Action.OpenDevCashFlow`.
  - `zero-core/.../settings/DefaultSettingsViewModel.kt` — inject handler + handle action.
  - `zero-core/.../settings/SettingsComponent.kt` — `@BindsInstance` + builder + factory param.
  - `zero-core/.../settings/SettingsViewProvider.kt:176-187` — second `MoreRow` in the Developer section.
  - `zero-core/src/main/res/values/strings.xml:128-130` — two new strings.
  - `app/.../screens/MainActivityScreenComponent.kt:949-975` — pass handler + register nav entry.

---

## Task 1: Cash-flow data factory (TDD)

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/CashflowReportData.kt`
- Test: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/chart/CashflowReportTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class CashflowReportTest {

    private val report = cashflowReport()

    @Test fun `totals are sums of the 6-month flow`() {
        assertEquals(31100f, report.totalIn)
        assertEquals(24290f, report.totalOut)
        assertEquals(6810f, report.net)
    }

    @Test fun `savings rate is net over income, rounded`() {
        assertEquals(22, report.savingsRate)
    }

    @Test fun `savings trend is per-month savings rate, oldest to newest`() {
        assertEquals(listOf(21f, 14f, 28f, 23f, 21f, 26f), report.savingsTrend)
        assertEquals(14, report.savingsRateMin)
        assertEquals(28, report.savingsRateMax)
    }

    @Test fun `latest month is the newest flow row`() {
        assertEquals("Apr", report.latest.label)
        assertEquals(5050f, report.latest.moneyIn)
        assertEquals(3760f, report.latest.moneyOut)
    }

    @Test fun `income shares are each source over total income, rounded`() {
        assertEquals(listOf("Salary", "Freelance", "Interest"), report.incomeSources.map { it.name })
        assertEquals(listOf(93, 6, 2), report.incomeSources.map { it.sharePercent })
    }

    @Test fun `comparisons are now minus prior, flagged money vs points`() {
        assertEquals(
            listOf("Money in" to 1700f, "Money out" to 190f, "Savings rate" to 4f),
            report.comparisons.map { it.label to it.delta },
        )
        assertEquals(listOf(true, true, false), report.comparisons.map { it.isMoney })
        assertEquals(listOf(31100f, 24290f, 22f), report.comparisons.map { it.nowValue })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "*CashflowReportTest*"`
Expected: FAIL — `cashflowReport` unresolved.

- [ ] **Step 3: Write the data file + factory**

```kotlin
// Cash-flow report mock data + projection. Swatch colors are entity colors (not theme roles),
// so this file is ZeroThemeBypass-exempt like ChartMockData.
@file:Suppress("ZeroThemeBypass")

package com.hluhovskyi.zero.ui.chart

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@androidx.compose.runtime.Immutable
internal data class CashflowReport(
    val net: Float,
    val totalIn: Float,
    val totalOut: Float,
    val savingsRate: Int,
    val months: List<MonthlyFlow>,
    val latest: MonthlyFlow,
    val savingsTrend: List<Float>,
    val savingsRateMin: Int,
    val savingsRateMax: Int,
    val incomeSources: List<IncomeShare>,
    val comparisons: List<PeriodComparison>,
)

@androidx.compose.runtime.Immutable
internal data class MonthlyFlow(val label: String, val moneyIn: Float, val moneyOut: Float)

@androidx.compose.runtime.Immutable
internal data class IncomeShare(val name: String, val amount: Float, val sharePercent: Int, val swatch: Color)

@androidx.compose.runtime.Immutable
internal data class PeriodComparison(val label: String, val nowValue: Float, val delta: Float, val isMoney: Boolean)

// Numbers straight from the Analytics design (CashflowReport in analytics-screens.jsx).
private val INCOME = listOf(
    Triple("Salary", 28800f, Color(0xFF7FD18C)),
    Triple("Freelance", 1800f, Color(0xFF9CC0FF)),
    Triple("Interest", 500f, Color(0xFFBCAAA4)),
)
private const val PRIOR_IN = 29400f
private const val PRIOR_OUT = 24100f
private const val PRIOR_SAVINGS_RATE = 18

/** Projects the fixed mock data into the cash-flow report UI model. All arithmetic lives here. */
internal fun cashflowReport(): CashflowReport {
    val months = ChartMockData.flow6.map { MonthlyFlow(it.first, it.second, it.third) }
    val totalIn = months.sumOf { it.moneyIn.toDouble() }.toFloat()
    val totalOut = months.sumOf { it.moneyOut.toDouble() }.toFloat()
    val net = totalIn - totalOut
    val savingsRate = (net / totalIn * 100).roundToInt()
    val savingsTrend = months.map { ((it.moneyIn - it.moneyOut) / it.moneyIn * 100).roundToInt().toFloat() }
    val incomeSources = INCOME.map { (name, amount, swatch) ->
        IncomeShare(name, amount, (amount / totalIn * 100).roundToInt(), swatch)
    }
    val comparisons = listOf(
        PeriodComparison("Money in", totalIn, totalIn - PRIOR_IN, isMoney = true),
        PeriodComparison("Money out", totalOut, totalOut - PRIOR_OUT, isMoney = true),
        PeriodComparison("Savings rate", savingsRate.toFloat(), (savingsRate - PRIOR_SAVINGS_RATE).toFloat(), isMoney = false),
    )
    return CashflowReport(
        net = net,
        totalIn = totalIn,
        totalOut = totalOut,
        savingsRate = savingsRate,
        months = months,
        latest = months.last(),
        savingsTrend = savingsTrend,
        savingsRateMin = savingsTrend.min().toInt(),
        savingsRateMax = savingsTrend.max().toInt(),
        incomeSources = incomeSources,
        comparisons = comparisons,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "*CashflowReportTest*"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/CashflowReportData.kt zero-ui/src/test/java/com/hluhovskyi/zero/ui/chart/CashflowReportTest.kt
git commit -m "feat(zero-ui): cash-flow report data projection"
```

---

## Task 2: Cash-flow report screen

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/CashflowReportScreen.kt`

Build `@Composable fun CashflowReportScreen(onBack: () -> Unit)` — collect `cashflowReport()` once with `remember`, render top→bottom. **Structure and idioms follow `ChartsGalleryScreen.kt`** (same package): `@file:Suppress("HardcodedComposableString")`, root `Column(...).background(ZeroTheme.colors.surface)`, `DetailTopBar(title = "Cash flow", onBack = onBack)`, then a scrolling `Column` (`verticalScroll`, horizontal padding 16dp, `Arrangement.spacedBy(16.dp)`). Private section composables, each reading only precomputed fields.

- [ ] **Step 1: Local formatters (mirror the design's `fmt0`/`fmtSign`)**

```kotlin
private fun fmt0(n: Float): String = "$" + "%,d".format(n.roundToInt())
private fun fmtSign(n: Float): String =
    (if (n >= 0) "+$" else "–$") + "%,d".format(abs(n).roundToInt())
```

- [ ] **Step 2: Hero card** — `chartHeroSurface` background, `RoundedCornerShape(20.dp)`, padding `18.dp`. "NET CASH FLOW" label (`chartHeroContentDim`, 11sp Bold, uppercase, letterSpacing `0.1.em`), `fmtSign(report.net)` (32sp ExtraBold, `chartHeroContent`). Then a `Row(spacedBy(10.dp))` of three weight(1f) tiles: **In** (`chartCashIn.copy(alpha=0.12f)` bg, label dim, `fmt0(report.totalIn)` in `chartCashIn`), **Out** (`chartCashOut.copy(alpha=0.12f)`, `fmt0(report.totalOut)` in `chartCashOut`), **Saved** (`chartHeroContent.copy(alpha=0.06f)`, `"${report.savingsRate}%"` in `chartHeroContent`). Tile label 10sp Bold uppercase dim; value 17sp ExtraBold.

- [ ] **Step 3: "By month" card** — a `Card`/`Column` on `surfaceContainerLowest`, `RoundedCornerShape(20.dp)`, padding 16dp. Header `Row(SpaceBetween)`: "By month" (13sp Bold onSurface) + a caption `"${report.latest.label} in ${fmt0(report.latest.moneyIn)}  out ${fmt0(report.latest.moneyOut)}"` (the "in" part in `chartCashIn`, "out" in `chartCashOut`, 12sp Bold). Then the chart built from the precomputed series:

```kotlin
val inC = ZeroTheme.colors.chartCashIn
val outC = ZeroTheme.colors.chartCashOut
BarChart(
    BarChartData(report.months.map { BarGroup(it.label, listOf(BarValue(it.moneyIn, inC), BarValue(it.moneyOut, outC))) }),
    Modifier.fillMaxWidth(),
)
```

Footer Text "Tap a month for its breakdown" (11sp, `onSurfaceVariant`, centered, top padding 10dp).

- [ ] **Step 4: "Savings rate" card** — same card chrome. Header `Row(Bottom, SpaceBetween)`: left column "SAVINGS RATE" (11sp Bold `onSurfaceVariant` uppercase) over `"${report.savingsRate}%"` (22sp ExtraBold `primary`) + " avg" (12sp `onSurfaceVariant`); right "${report.savingsRateMin}–${report.savingsRateMax}% range" (12sp Bold `onSurfaceVariant`). Then `LineChart(LineChartData(report.savingsTrend), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(50.dp))`. Then a `Row` of the month labels (`report.months.map { it.label }`), each `Modifier.weight(1f)`, centered, 10sp `onSurfaceVariant`.

- [ ] **Step 5: Income sources** — a section header "INCOME SOURCES" (11sp Bold `onSurfaceVariant`, letterSpacing). For each `report.incomeSources` row: `Row` on `surfaceContainerLowest` card (`RoundedCornerShape(14.dp)`, padding `12.dp`, `spacedBy(12.dp)`) — a `CategoryIconView(color = source.swatch, size = 38.dp) { Icon(icon, tint = ZeroTheme.colors.surface) }` where `icon` is chosen by index: `0 → Icons.Outlined.Payments`, `1 → Icons.Outlined.Work`, `2 → Icons.Outlined.Savings`; then a `Column(weight 1f)` with a name/amount `Row(SpaceBetween)` (name 14sp Medium onSurface, `fmt0(source.amount)` 14sp ExtraBold onSurface), a share progress bar (`Box` height 5dp `surfaceContainer` track, inner `Box` `fillMaxWidth(source.sharePercent/100f)` in `source.swatch`), and "${source.sharePercent}% of income" (11sp `onSurfaceVariant`). Verify the three icons resolve against `material-icons-extended` (already a dep — `ChartsGalleryScreen`/Settings use `Icons.Outlined.BarChart`); swap to a resolvable Outlined icon if any is missing.

- [ ] **Step 6: "Vs previous 6 months"** — header "VS PREVIOUS 6 MONTHS"; a single `surfaceContainerLowest` card (`RoundedCornerShape(16.dp)`). For each `report.comparisons` row: a `Row(SpaceBetween)`, top border (1dp `surfaceContainer`) on all but the first. Left = `row.label` (14sp onSurface). Right = `Row(spacedBy(10.dp))`: value `if (row.isMoney) fmt0(row.nowValue) else "${row.nowValue.toInt()}%"` (14sp Bold onSurface) + a delta chip — `val up = row.delta >= 0`; text `if (row.isMoney) fmtSign(row.delta) else "${if (up) "+" else ""}${row.delta.toInt()} pts"`, color `if (up) ZeroTheme.colors.secondary else ZeroTheme.colors.error` (12sp Bold).

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/CashflowReportScreen.kt
git commit -m "feat(zero-ui): cash-flow report screen"
```

---

## Task 3: Settings + navigation wiring

Mirror the `Dev.Charts` entry **1:1**; `OnDevChartsSelectedHandler` / `OpenDevCharts` / `settings_dev_charts` are the structural analogs for every step below.

**Files & changes:**
- [ ] **Step 1: Destination** — `app/.../navigation/Destinations.kt`, in `sealed interface Dev`, add beside `Charts`:

```kotlin
object CashFlow : Dev, Destination by destinationOf("dev/cashflow")
```

- [ ] **Step 2: Handler** — create `zero-core/.../settings/OnDevCashFlowSelectedHandler.kt` as an exact copy of `OnDevChartsSelectedHandler.kt` (fun interface with `onSelected()` + `object Noop`), renamed.

- [ ] **Step 3: ViewModel action** — `SettingsViewModel.kt`: add `object OpenDevCashFlow : Action` beside `OpenDevCharts`.

- [ ] **Step 4: ViewModel handling** — `DefaultSettingsViewModel.kt`: add constructor param `private val onDevCashFlowSelected: OnDevCashFlowSelectedHandler`, and an `is SettingsViewModel.Action.OpenDevCashFlow ->` branch that mirrors the `OpenDevCharts` branch (`coroutineScope.launch(Dispatchers.Main) { onDevCashFlowSelected.onSelected() }`).

- [ ] **Step 5: Component** — `SettingsComponent.kt`: add the `.onDevCashFlowSelectedHandler(OnDevCashFlowSelectedHandler.Noop)` default, the `fun onDevCashFlowSelectedHandler(handler): Builder` `@BindsInstance`, and thread `onDevCashFlowSelected` through the factory params — each beside its `onDevCharts` counterpart.

- [ ] **Step 6: Strings** — `zero-core/src/main/res/values/strings.xml`, after the `settings_dev_charts*` lines:

```xml
<string name="settings_dev_cashflow">Cash flow</string>
<string name="settings_dev_cashflow_description">Preview the cash-flow report</string>
```

- [ ] **Step 7: ViewProvider row** — `SettingsViewProvider.kt`, inside the `if (state.showDeveloperOptions)` `MoreSection`, add a second `MoreRow` after the Charts row:

```kotlin
MoreRow(
    icon = Icons.Outlined.ShowChart,
    primaryText = stringResource(R.string.settings_dev_cashflow),
    secondaryText = stringResource(R.string.settings_dev_cashflow_description),
    onClick = { viewModel.perform(SettingsViewModel.Action.OpenDevCashFlow) },
)
```

(`Icons.Outlined.ShowChart` is in material-icons-extended; if unresolved use `Icons.AutoMirrored.Outlined.ShowChart` or another resolvable Outlined icon.)

- [ ] **Step 8: App wiring** — `MainActivityScreenComponent.kt`: in `settingsNavigationEntry` add `.onDevCashFlowSelectedHandler { navigator.navigateTo(Destinations.Dev.CashFlow) }` beside the Charts line; add a nav entry mirroring `devChartsNavigationEntry`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun devCashFlowNavigationEntry(
    navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(Destinations.Dev.CashFlow) {
    CashflowReportScreen(onBack = { navigator.back() })
}
```

Add the `import com.hluhovskyi.zero.ui.chart.CashflowReportScreen` beside the existing `ChartsGalleryScreen` import.

- [ ] **Step 9: Build to verify wiring compiles (Dagger graph + app)**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (Dagger resolves the new `@BindsInstance`).

- [ ] **Step 10: Commit**

```bash
git add app zero-core
git commit -m "feat: dev entry point for the cash-flow report screen"
```

---

## Task 4: Verification

- [ ] **Step 1: Full gate (one invocation)**

Run: `./gradlew spotlessApply testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL. `git add`/commit anything `spotlessApply` reformats.

- [ ] **Step 2: Install + UI inspection** — build/install debug, open Settings → Developer → **Cash flow**, and use `zero-project:android-ui-inspector` to confirm each section renders on device: hero net/in/out/saved, the 6 in/out bar groups, the savings-rate sparkline + labels, the three income rows with share bars, and the three comparison rows with signed deltas. Screenshot. A UI task is not done until the inspector confirms it.

- [ ] **Step 3: Commit any inspector-driven fixes**, then proceed to the PR (Step 6 of `/lets-do`).

---

## Self-Review

- **Spec coverage:** header/period chip (Task 2 hero region + DetailTopBar), hero (T2 s2), by-month bars (T2 s3), savings trend (T2 s4), income sources (T2 s5), vs-prior (T2 s6), factory + no-calc-in-views (T1), reuse of existing charts/tokens (T1/T2), thin wiring + strings (T3), unit + lint + UI verification (T1/T4). All covered.
- **Type consistency:** `CashflowReport`, `MonthlyFlow`, `IncomeShare(... swatch)`, `PeriodComparison(label, nowValue, delta, isMoney)` used identically across the test (T1), factory (T1), and screen (T2). `cashflowReport()` name consistent. `BarChartData`/`BarGroup`/`BarValue`/`LineChartData` match the existing `ChartData.kt` signatures.
- **Placeholders:** none — the factory and test are complete code; the composable steps name exact tokens, sizes, and the precomputed fields they read, with the gallery as the structural analog.
- **Risk notes:** the two Material icon choices (income badges, settings row) are flagged in-step to swap for a resolvable Outlined icon if absent — the only non-deterministic detail.
</content>
