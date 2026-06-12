# Analytics Hub Entry Point — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Categories bottom-nav tab with an **Analytics hub** screen: a cash-flow hero chart + a ranked category breakdown (donut + rows), scoped to the last 6 months.

**Architecture:** A new `AnalyticsUseCase` (zero-api interface, `DefaultAnalyticsUseCase` in zero-core) owns **all** aggregation — it reads transactions over a `DateRange` (new `TransactionRepository.Criteria.AllBetween`) plus category metadata, and emits cash-flow buckets + a ranked breakdown with recent-vs-prior trend. A new `AnalyticsComponent`/`AnalyticsViewModel`/`AnalyticsViewProvider` feature (zero-core) projects that into UI shape and renders it with the **existing** `zero-ui` charts (`BarChart`, `DonutChart`) and theme palette (`chartCashIn/Out`, `chartHeroSurface`). The Categories tab is swapped for Analytics in the bottom bar; the existing Categories screen stays reachable via "See all categories".

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Room, kotlinx-datetime, kotlinx-coroutines Flow. JUnit/Mockito for tests.

---

## Design → code mapping

Source design: `ui_kits/zero/Analytics Exploration.html` → the `AnalyticsFull` destination (in `analytics-screens.jsx` + `analytics-kit.jsx`). The hub composes, top-to-bottom:

| Design element | This plan |
|---|---|
| Header `Analytics` + `PeriodChip("Last 6 months")` | Title + a **static** period chip (string resource; date-range picker is out of scope) |
| `FlowCard` — navy hero, net + In/Out, 6 grouped in/out bars | `BarChart` (grouped) on `chartHeroSurface`, bars `chartCashIn`/`chartCashOut` |
| `BreakdownCard` — donut (top-6 + Other) + legend (top-3 + Other) + top-5 rows (trend chip + bar + share%) + "See all N" | `DonutChart` + `CategoryIconView` rows; "See all" → existing Categories screen |
| `InsightsStrip` — 3 derived cards | Final, **cuttable** task (Task 9) |
| `FlowCard` "View cash-flow trends" link; tappable insight/months | **Omitted** — those open report screens that are out of scope |

## Scope

**In:** the `AnalyticsFull` hub destination; the data + domain to compute it over any `DateRange` (hardcoded to last 6 months); bottom-bar swap; "See all categories" → existing Categories screen.

**Out (do not build):** the period/date-range picker UI (chip is static), the Cash-flow report, the Spending report, the merged Categories screen, scoped/filter reports, the Accounts net-worth hero, per-category detail changes, favorites/★. These are other sections of the same design canvas — ignore them.

## Binding principle — calculations live in the UseCase/ViewModel, never the View

Enforced by the repo's `StateCollectionDerivationDetector` / `ViewProviderDependencyDetector` lint and by [architecture.md](../../agents/architecture.md) "ViewModel UI Shape". For this feature specifically:

- **`DefaultAnalyticsUseCase`** does all domain truth: currency conversion, month bucketing, per-category sums, recent-vs-prior split, sorting by spend.
- **`DefaultAnalyticsViewModel`** does all screen-shape math: top-6/top-5/"Other" splitting, `share%`, `trend%` → semantic enum, `Amount`→`Float` for the charts, label strings.
- **`AnalyticsViewProvider`** does **zero arithmetic** — no `.filter`/`.sortedBy`/`.sumOf`, no `value/total`, no null-branching on raw fields. Its only transformation is attaching theme/category **colors** to the ViewModel's already-computed numeric series when constructing `BarChartData`/`DonutChartData`, plus `AmountFormatter` + `stringResource`.

## File structure

- **Create** `zero-api/.../analytics/AnalyticsUseCase.kt` — domain contract + read model + `Noop`.
- **Create** `zero-core/.../analytics/DefaultAnalyticsUseCase.kt` — aggregation (the calculation engine).
- **Create** `zero-core/.../analytics/Analytics{Component,ViewModel,ViewProvider}.kt` + `DefaultAnalyticsViewModel.kt` + `OnSeeAllCategoriesHandler.kt` + `OnAnalyticsCategorySelectedHandler.kt` (scaffolded).
- **Create** `app/src/main/res/drawable/ic_analytics_24.xml`.
- **Modify** `zero-api/.../transactions/TransactionRepository.kt` (+ Room query/impl in `zero-database`) — `Criteria.AllBetween`.
- **Modify** `Destinations.kt`, `MainActivityScreenComponent.kt`, `ApplicationComponent.kt`, `ActivityComponent.kt` — destination + nav entry + DI.
- **Modify** `BottomBarViewModel.kt`, `DefaultBottomBarViewModel.kt` — Categories→Analytics.
- **Modify** `zero-core/.../res/values/strings.xml` (or the module's string file) — user-facing text.

---

### Task 1: `TransactionRepository.Criteria.AllBetween` (cash-flow + breakdown data source)

One range-scoped query returns every non-transfer-relevant transaction for the period; the use case classifies + buckets. `ForCategoryBetween` is the exact template — `AllBetween` is that query minus the category predicate.

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- Modify: the Room DAO + `TransactionRepository` impl in `zero-database` (follow the existing `ForCategoryBetween` query — same `from`/`to` bounds, same `userId` + soft-delete filters, no category filter)
- Test: `zero-database` repository test alongside the existing `ForCategoryBetween` test

- [ ] **Step 1 — Add the criteria.** In `TransactionRepository.Criteria`, next to `ForCategoryBetween`:

```kotlin
data class AllBetween(
    val from: LocalDate,
    val to: LocalDate,
) : Criteria<List<Transaction>>
```

- [ ] **Step 2 — Write the failing repository test.** Mirror the existing `ForCategoryBetween` repo test: insert expense + income + transfer across two dates, query `AllBetween(from, to)`, assert all three types within `[from, to]` are returned and ones outside the range are excluded. Run the zero-database test task; expect FAIL (`AllBetween` not handled).

- [ ] **Step 3 — Implement the Room query + repo branch.** Add a DAO query `selectAllBetween(userId, from, to)` (copy `ForCategoryBetween`'s query, drop the `categoryId` clause) and a `when`-branch in the repository that maps `Criteria.AllBetween` → that query → `Transaction` mapping (reuse the existing row→`Transaction` mapper). Keep the `uncheckedCast()` tail.

- [ ] **Step 4 — Run the test; expect PASS.**

- [ ] **Step 5 — Commit.** `git add -A && git commit -m "feat(data): add TransactionRepository.Criteria.AllBetween"`

---

### Task 2: `AnalyticsUseCase` contract (zero-api)

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/analytics/AnalyticsUseCase.kt`

- [ ] **Step 1 — Write the interface.** Model after `CategorySpendingUseCase.kt` (same package style, `Noop`). Reuse `com.hluhovskyi.zero.common.DateRange` (`start`/`end`).

```kotlin
package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AnalyticsUseCase {

    fun query(range: DateRange): Flow<Analytics>

    data class Analytics(
        val totalIn: Amount,
        val totalOut: Amount,
        val cashFlow: List<CashFlowBucket>,     // chronological, one per month in range
        val breakdown: List<CategorySpend>,     // expense categories, sorted by amount desc
    )

    /** One month bucket. [label] is the bucket's short month name (e.g. "Apr"). */
    data class CashFlowBucket(
        val label: String,
        val income: Amount,
        val expense: Amount,
    )

    /** A category's spend over the range, with halves for the recent-vs-prior trend. */
    data class CategorySpend(
        val categoryId: Id.Known,
        val amount: Amount,
        val transactionCount: Int,
        val recentAmount: Amount,   // second half of the range
        val priorAmount: Amount,    // first half of the range
    )

    object Noop : AnalyticsUseCase {
        override fun query(range: DateRange): Flow<Analytics> = emptyFlow()
    }
}
```

- [ ] **Step 2 — Build.** Run `./gradlew :zero-api:compileKotlin 2>&1 | tail -5`. Expect success.
- [ ] **Step 3 — Commit.** `git add -A && git commit -m "feat(analytics): add AnalyticsUseCase contract"`

---

### Task 3: `DefaultAnalyticsUseCase` — the calculation engine (zero-core)

Combines **two** flows: `transactionRepository.query(AllBetween)` (reactive) and `categoriesQueryUseCase.queryAll()` (for the category set + EXPENSE filter). Conversion is `suspend`, so aggregate inside `mapLatest`. Pattern reference: `DefaultCategorySpendingUseCase.kt` (conversion + period resolve) and `DefaultCategoryViewModel.attachOnMain()` (the category × spend join).

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/analytics/DefaultAnalyticsUseCase.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/analytics/DefaultAnalyticsUseCaseTest.kt`

- [ ] **Step 1 — Write failing tests.** Use fakes/Mockito like existing zero-core use-case tests ([testing.md](../../agents/testing.md)). Cover, with a fixed `Clock`/`ZoneProvider` and a stub `CurrencyConvertUseCase` that returns its input (single currency):

```kotlin
// Given a 6-month range and a handful of Expense/Income/Transfer rows across months:
// 1. cashFlow has one bucket per calendar month in the range, chronological, labelled by short month name.
// 2. A month with no rows still yields a bucket with income/expense = zero (full range is charted).
// 3. income = Σ Income amounts per month; expense = Σ Expense amounts per month; Transfers ignored in both.
// 4. totalIn / totalOut = Σ over buckets.
// 5. breakdown: one CategorySpend per EXPENSE category that has spend, sorted by amount desc.
// 6. recentAmount = Σ in the second half of the range; priorAmount = Σ in the first half (split at the midpoint date).
// 7. Income categories and transfers never appear in breakdown.
```

Run the zero-core test task for this class; expect FAIL (unresolved `DefaultAnalyticsUseCase`).

- [ ] **Step 2 — Implement.** All arithmetic lives here.

```kotlin
package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.flow.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth          // or compute (year, month) pairs if YearMonth is unavailable
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit

internal class DefaultAnalyticsUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
) : AnalyticsUseCase {

    override fun query(range: DateRange): Flow<AnalyticsUseCase.Analytics> = combine(
        transactionRepository.query(TransactionRepository.Criteria.AllBetween(range.start, range.end)),
        categoriesQueryUseCase.queryAll().onStartWithEmptyList(),
    ) { transactions, categories -> transactions to categories }
        .mapLatest { (transactions, categories) -> aggregate(range, transactions, categories) }

    private suspend fun aggregate(
        range: DateRange,
        transactions: List<TransactionRepository.Transaction>,
        categories: List<CategoriesQueryUseCase.Category>,
    ): AnalyticsUseCase.Analytics {
        // Convert every relevant tx to primary once; carry (date, type, categoryId, amount).
        // Cash flow: pre-seed one mutable bucket per month from range.start..range.end (inclusive),
        //   keyed by (year, month); add Income→income, Expense→expense; ignore Transfer.
        // Breakdown: only EXPENSE categories present in `categories`; sum full / recent / prior halves
        //   (midpoint = range.start + (daysBetween/2)); sort by amount desc.
        // totalIn/totalOut = fold over buckets.
        // ... (implement per the test cases above)
    }
}
```

> Implementation notes for the engine (no placeholders — these are the rules to code):
> - **Month buckets**: iterate `var d = LocalDate(range.start.year, range.start.month, 1)` while `d <= range.end`, `d = d.plus(1, DateTimeUnit.MONTH)`; label = `d.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase)`.
> - **Bucket assignment**: a tx falls in the bucket matching `tx.dateTime.date.year`/`.month`.
> - **Convert**: `currencyConvertUseCase.convertToPrimary(tx.amount, tx.currencyId)` (suspend) — same call as `DefaultCategorySpendingUseCase`.
> - **Midpoint**: `range.start.plus(range.start.daysUntil(range.end) / 2, DateTimeUnit.DAY)`; recent = `[midpoint, end]`, prior = `[start, midpoint)`.
> - Verify `onStartWithEmptyList` exists at that import path; if not, use the equivalent flow extension from [architecture.md](../../agents/architecture.md) "Flow Composition".

- [ ] **Step 3 — Run the tests; expect PASS.**
- [ ] **Step 4 — Commit.** `git add -A && git commit -m "feat(analytics): add DefaultAnalyticsUseCase aggregation"`

---

### Task 4: Scaffold the Analytics feature

- [ ] **Step 1 — Scaffold.** Run `scaffold-feature` with: `name = Analytics`, `package = analytics`, `handlers = none` (custom handlers added below), `useCase = skip` (already built). This generates `AnalyticsComponent`, `AnalyticsViewModel`, `DefaultAnalyticsViewModel`, `AnalyticsViewProvider` under `zero-core/.../analytics/`.

- [ ] **Step 2 — Add handler files.** Create two `fun interface` handlers ([architecture.md](../../agents/architecture.md) — `OnXxxHandler` + `Noop`):

```kotlin
// OnSeeAllCategoriesHandler.kt
package com.hluhovskyi.zero.analytics
fun interface OnSeeAllCategoriesHandler {
    fun onSeeAllCategories()
    companion object { val Noop = OnSeeAllCategoriesHandler { } }
}
```

```kotlin
// OnAnalyticsCategorySelectedHandler.kt
package com.hluhovskyi.zero.analytics
import com.hluhovskyi.zero.common.Id
fun interface OnAnalyticsCategorySelectedHandler {
    fun onSelected(categoryId: Id.Known)
    companion object { val Noop = OnAnalyticsCategorySelectedHandler { } }
}
```

- [ ] **Step 3 — Wire the component.** Edit `AnalyticsComponent`: `Dependencies` = `{ transactionRepository, categoriesQueryUseCase, currencyConvertUseCase, amountFormatter, imageLoader, clock, zoneProvider }` (mirror `CategoryComponent.Dependencies`); add `@BindsInstance` `onSeeAllCategoriesHandler` + `onAnalyticsCategorySelectedHandler` to the `Builder` (Noop defaults in `companion.builder`). In `Module`, `@Provides` `AnalyticsUseCase = DefaultAnalyticsUseCase(...)` and pass it + handlers + `clock`/`zoneProvider` into the ViewModel, and `amountFormatter`/`imageLoader` into the ViewProvider.

- [ ] **Step 4 — Compile.** `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5`. Expect success.
- [ ] **Step 5 — Commit.** `git add -A && git commit -m "feat(analytics): scaffold Analytics component + handlers"`

---

### Task 5: `DefaultAnalyticsViewModel` — project to UI shape

Computes the **last-6-months** range from the clock, observes `AnalyticsUseCase.query(range)`, and emits screen-shape data. All top-N/Other/share%/trend math lives here.

**Files:**
- Modify: `AnalyticsViewModel.kt` (state/items), `DefaultAnalyticsViewModel.kt`
- Test: `DefaultAnalyticsViewModelTest.kt`

- [ ] **Step 1 — Define the UI shape** in `AnalyticsViewModel`:

```kotlin
data class State(
    val netAmount: Amount = Amount.zero(),
    val totalIn: Amount = Amount.zero(),
    val totalOut: Amount = Amount.zero(),
    val cashFlow: List<FlowBar> = emptyList(),     // chronological
    val donut: List<Slice> = emptyList(),          // top 6 + Other
    val legend: List<LegendItem> = emptyList(),    // top 3 + Other
    val rows: List<CategoryRow> = emptyList(),     // top 5
    val categoryCount: Int = 0,
)
data class FlowBar(val label: String, val income: Float, val expense: Float)
/** colorScheme null = the aggregated "Other" slice. */
data class Slice(val colorScheme: ColorScheme?, val value: Float)
data class LegendItem(val colorScheme: ColorScheme?, val name: String?, val sharePercent: Int) // name null = "Other"
data class CategoryRow(
    val id: Id.Known,
    val name: String,
    val icon: Image,
    val colorScheme: ColorScheme,
    val amount: Amount,
    val sharePercent: Int,
    val trend: Trend,
)
sealed interface Trend {
    data object New : Trend
    data object Flat : Trend
    data class Up(val percent: Int) : Trend     // spent MORE than prior half
    data class Down(val percent: Int) : Trend   // spent LESS
}
sealed interface Action {
    data object SeeAllCategories : Action
    data class SelectCategory(val id: Id.Known) : Action
}
```

(`ColorScheme` = `com.hluhovskyi.zero.colors.ColorScheme` — domain, not Compose; `Image` = `com.hluhovskyi.zero.common.Image`.)

- [ ] **Step 2 — Write failing ViewModel tests** (fake `AnalyticsUseCase` emitting a known `Analytics`, fixed `Clock`/`ZoneProvider`):

```kotlin
// 1. cashFlow maps 1:1 from Analytics.cashFlow with income/expense as Float (Amount.value.toFloat()).
// 2. donut = top 6 categories by amount + a single "Other" slice (colorScheme null) summing the rest;
//    no "Other" slice when ≤ 6 categories.
// 3. legend = top 3 + "Other"; sharePercent rounds (amount / totalBreakdown * 100).
// 4. rows = top 5; each sharePercent computed; trend: priorAmount==0 → New; |Δ|<2% → Flat;
//    recent>prior → Up(pct); recent<prior → Down(pct), pct = round(|recent-prior|/prior*100).
// 5. netAmount = totalIn - totalOut; categoryCount = breakdown.size.
// 6. perform(SeeAllCategories) invokes onSeeAllCategoriesHandler on Dispatchers.Main.
```

Run; expect FAIL.

- [ ] **Step 3 — Implement `DefaultAnalyticsViewModel`.** Compute range in the IO scope: `val today = clock.now().toLocalDateTime(zone).date; val range = DateRange(today.minus(6, MONTH).startOfMonth?, today)` — last 6 full months (match the engine's bucketing so 6 buckets appear). Observe `analyticsUseCase.query(range)`, map to `State` (top-6/top-5/Other/share/trend math here), `mutableState.update`. `perform` dispatches handlers on `Dispatchers.Main` ([scaffold-feature](../../../.claude/marketplace/plugins/zero-project/skills/scaffold-feature/SKILL.md) "Handler dispatch"). No `ColorScheme→Compose` and no `AmountFormatter` here.

- [ ] **Step 4 — Run tests; expect PASS.**
- [ ] **Step 5 — Commit.** `git add -A && git commit -m "feat(analytics): project AnalyticsUseCase into screen state"`

---

### Task 6: `AnalyticsViewProvider` — render (zero arithmetic)

Renders the hub with existing `zero-ui` charts + theme. Reference the gallery's `flowBars`/donut usage in `ChartsGalleryScreen.kt`, and `CategoryViewProvider.kt` for `CategoryIconView`/`toUi`/`AmountFormatter`/`stringResource`.

**Files:**
- Modify: `AnalyticsViewProvider.kt`

- [ ] **Step 1 — Implement the layout.** A `LazyColumn`/`Column(verticalScroll)` on `ZeroTheme.colors.surface`:
  1. **Header row** — `Text("Analytics")` (`stringResource`) + static period chip `Text(stringResource(R.string.analytics_period_last_6_months))` styled like `PeriodChip` (no click).
  2. **Flow hero** — card `background(ZeroTheme.colors.chartHeroSurface, RoundedCornerShape(22.dp))`: net (`AmountFormatter` with sign) + In/Out legend (`chartCashIn`/`chartCashOut`, amounts via `AmountFormatter`), then
     ```kotlin
     BarChart(
         BarChartData(state.cashFlow.map { BarGroup(it.label, listOf(
             BarValue(it.income, ZeroTheme.colors.chartCashIn),
             BarValue(it.expense, ZeroTheme.colors.chartCashOut),
         )) }),
         Modifier.fillMaxWidth(),
     )
     ```
  3. **Breakdown card** — `background(surfaceContainerLowest, RoundedCornerShape(22.dp))`: title "By category" (`stringResource`) + trend key;
     ```kotlin
     DonutChart(
         DonutChartData(state.donut.map { DonutSegment(it.value, it.colorScheme?.toUi()?.primary ?: ZeroTheme.colors.outlineVariant) }),
         Modifier.size(140.dp),
     ) { /* center: "Spent" + AmountFormatter(state.totalOut) */ }
     ```
     legend items (swatch color = `colorScheme?.toUi()` or `outlineVariant`; name = `it.name ?: stringResource(R.string.analytics_other)`; `"${it.sharePercent}%"`), then the top-5 `state.rows`: `CategoryIconView(colorScheme = row.colorScheme.toUi())`, name, `TrendChip` (renders `row.trend`: `Up`→`error`+up-arrow+`"${percent}%"`, `Down`→`secondary`+down-arrow, `Flat`→`"—"`, `New`→`stringResource(R.string.analytics_trend_new)`), `AmountFormatter(row.amount)`, a share bar `Box(width fraction = row.sharePercent/100f...)` — **the only ratio**, and it's `Float`-from-Int with no aggregation; if the lint flags it, precompute `barFraction: Float` in the ViewModel instead.
  4. **"See all" row** — `Text(stringResource(R.string.analytics_see_all, state.categoryCount))`, `clickable { viewModel.perform(SeeAllCategories) }`.

  Each `row.colorScheme.toUi()` / `BarValue(...)` / `DonutSegment(...)` is a pure constructor call on ViewModel-provided numbers — **no `.filter`/`.sortedBy`/`.sumOf`/`value/total` in this file**.

- [ ] **Step 2 — Compile.** `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5`. Expect success. (If `StateCollectionDerivationDetector` flags the share bar, move `barFraction` to `CategoryRow` in the ViewModel and recompile.)
- [ ] **Step 3 — Commit.** `git add -A && git commit -m "feat(analytics): render Analytics hub with charts"`

---

### Task 7: Bottom-bar swap + analytics icon

**Files:**
- Create: `app/src/main/res/drawable/ic_analytics_24.xml`
- Modify: `BottomBarViewModel.kt`, `DefaultBottomBarViewModel.kt`

- [ ] **Step 1 — Add the drawable.** Bar-chart icon, from the design's analytics glyph (`M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z`):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zm5.6,8H19v6h-2.8v-6z" />
</vector>
```

- [ ] **Step 2 — Swap the companion id.** In `BottomBarViewModel.companion`, replace `CategoriesId = Id.Known("categories")` with `AnalyticsId = Id.Known("analytics")`.

- [ ] **Step 3 — Swap the item + routing** in `DefaultBottomBarViewModel`: the 4th `Item` uses `AnalyticsId` + `drawable("ic_analytics_24")`; in `perform`, `AnalyticsId -> Destinations.Analytics`; in `toBottomBarId()`, `Destinations.Analytics.route -> AnalyticsId`. (`Destinations.Analytics` added in Task 8 — compile after both.)

- [ ] **Step 4 — Commit.** `git add -A && git commit -m "feat(analytics): swap Categories tab for Analytics in bottom bar"`

---

### Task 8: Destination + nav entry + DI wiring

**Files:**
- Modify: `Destinations.kt`, `MainActivityScreenComponent.kt`, `ApplicationComponent.kt`, `ActivityComponent.kt`

- [ ] **Step 1 — Add the destination.** In `Destinations.kt`: `object Analytics : Destination by destinationOf("analytics")`.

- [ ] **Step 2 — Provide the component builder.** Add `AnalyticsComponent.Builder` to `ApplicationComponent`/`ActivityComponent` exactly where `categoryComponentBuilder` is provided (same dependency set: `transactionRepository`, `categoriesQueryUseCase`, `currencyConvertUseCase`, `amountFormatter`, `imageLoader`, `clock`, `zoneProvider`). Add `analyticsComponentBuilder: AnalyticsComponent.Builder` to `MainActivityScreenComponent.Dependencies`.

- [ ] **Step 3 — Add the tab component + nav entry** in `MainActivityScreenComponent.Module`, mirroring `categoryTabComponent` + `categoryNavigationEntry` (add a `@ForAnalyticsTab` qualifier + `analyticsTab` abstract val attached in `attach()`):

```kotlin
@Provides @MainActivityScreenScope @ForAnalyticsTab
fun analyticsTabComponent(
    componentBuilder: AnalyticsComponent.Builder, navigator: Navigator, logger: Logger,
): AttachableViewComponent = componentBuilder
    .onSeeAllCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
    .onAnalyticsCategorySelectedHandler { categoryId ->
        navigator.navigateTo(
            Destinations.Category.Item.Detail,
            Destinations.Category.Item.CategoryId.withValue(categoryId),
        )
    }
    .logging(logger).build()

@Provides @IntoSet @MainActivityScreenScope
fun analyticsNavigationEntry(
    @ForAnalyticsTab component: AttachableViewComponent, navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(
    destination = Destinations.Analytics,
    displayOption = NavigatorEntry.DisplayOption.FullyVisible,
) { component.AttachWithView() }
```

Add `analyticsTab.attach()` to the `Closeables.merge(...)` in `attach()`. Keep `categoryNavigationEntry` + `Destinations.Category.All` registered (still reached from Analytics + transaction-edit).

- [ ] **Step 4 — Compile the app.** `./gradlew :app:compileDebugKotlin 2>&1 | tail -15`. Expect success (Dagger graph resolves).
- [ ] **Step 5 — Commit.** `git add -A && git commit -m "feat(analytics): register Analytics destination, nav entry, DI"`

---

### Task 9 (cuttable): InsightsStrip

Only if Tasks 1–8 are green and there's budget. A horizontally-scrolling row of 3 derived cards (Fastest growing / Biggest / Most frequent). The data already exists in `Analytics.breakdown` (amount, count, recent vs prior) — compute the three picks **in the ViewModel** as a `List<Insight>` (semantic: `kicker` enum + `categoryId`/name/icon/colorScheme + a pre-formatted-free sub value), render between the flow hero and the breakdown. If it adds material complexity, **drop it** and note so in the PR.

- [ ] Add `insights: List<Insight>` to state + VM computation + test; render in the ViewProvider; commit `feat(analytics): add insights strip`.

---

### Task 10: Verification

- [ ] **Step 1 — Gates (one invocation).** `./gradlew spotlessApply testDebugUnitTest lintDebug 2>&1 | tail -25`. Fix any failure. `git add -A` any spotless reformat + commit.
- [ ] **Step 2 — UI inspection.** Acquire the emulator (`./scripts/emulator/acquire`), install, open the Analytics tab. Use `zero-project:android-ui-inspector` to confirm: header + period chip, flow hero with 6 in/out bar pairs, donut + legend + 5 rows render with correct bounds; "See all categories" navigates to the existing Categories screen; bottom bar shows Analytics (not Categories) selected. Screenshot for the PR.
- [ ] **Step 3 — Architecture review.** This introduces a new use case + handlers + a DI tab + a repo criteria — run `zero-project:pr-architecture-review` on the branch diff; fold clear wins in, note the rest in the PR body.

---

## Self-review notes

- **Spec coverage:** header+chip (T6), flow hero (T3 data, T6 render), breakdown donut+rows+trend+share (T3/T5/T6), "see all"→Categories (T5 action, T8 wiring), bottom-bar swap (T7), any-range engine hardcoded to 6mo (T3/T5). InsightsStrip = T9. Out-of-scope screens intentionally excluded.
- **Calculations-in-views guardrail:** restated as the binding principle; T6 explicitly forbids arithmetic and gives the lint-fallback (precompute `barFraction` in the VM).
- **Type consistency:** `Analytics`/`CashFlowBucket`/`CategorySpend` (T2) consumed in T3/T5; `State`/`FlowBar`/`Slice`/`LegendItem`/`CategoryRow`/`Trend`/`Action` (T5) consumed in T6; `AllBetween` (T1) used in T3; handlers (T4) wired in T8.
- **Verify-before-build flags for the executor:** confirm `onStartWithEmptyList` import path; confirm `DateRange` fields (`start`/`end`); confirm `ColorScheme.toUi()` shape (`.primary`); confirm `YearMonth`/month-iteration API available in the project's kotlinx-datetime version (fall back to `(year, month)` pairs).
