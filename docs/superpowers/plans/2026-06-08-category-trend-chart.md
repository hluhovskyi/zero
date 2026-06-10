# Category Trend Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 6-month spending trend bar chart to the Category drill-down (`CategoryDetail`) screen, using the new `zero-ui` `BarChart`.

**Architecture:** Data comes from a new focused method `queryMonthlyTrend` on the existing `CategorySpendingUseCase` (one `ForCategoryBetween` window query, bucketed by month in Kotlin, zero-filled). The `CategoryDetailViewModel.State` gains a pre-shaped `trend` list; a new `TrendCard` composable in `CategoryDetailViewProvider` renders it via `BarChart`, placed in the collapsible `hero` slot below the existing tinted `HeroCard`. No DI changes — `categorySpendingUseCase` is already injected into the ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx-datetime, Dagger, JUnit/Mockito. New `zero-ui` chart components from `com.hluhovskyi.zero.ui.chart` (`BarChart`, `BarChartData`, `BarGroup`, `BarValue`).

---

## Design Reference

Design file: `ui_kits/zero/Analytics Exploration.html` → `CategoryDetailScreen` (section "Per-category analytics", artboard "Category drill-down"). The screen already exists in the app; the design enriches it with a **"6-month trend"** card between the tinted hero and the transaction list:

- A `surfaceLowest` rounded card, title "6-MONTH TREND".
- 6 vertical bars, one per month (oldest → newest), each labelled with its month abbrev below and its `$` amount above (`topLabel`).
- Bars tinted with the category's **primary color**; the current (last) month is full-opacity, the prior 5 are dimmed (~0.4 alpha).

The bar chart, value-on-top, current-month-highlighted shape is exactly what `BarChart` + `BarGroup.topLabel` + per-`BarValue.color` were built for. The debug gallery's `CategoryTrend()` (`zero-ui/.../chart/ChartsGalleryScreen.kt:91-106`) is the canonical build reference — reuse its structure, swapping the theme accent for the category's color scheme.

## File Structure

| File | Responsibility | Change |
|------|----------------|--------|
| `zero-api/.../categories/CategorySpendingUseCase.kt` | Domain contract | **Modify** — add `MonthlySpending` + `queryMonthlyTrend`, extend `Noop` |
| `zero-core/.../categories/DefaultCategorySpendingUseCase.kt` | Aggregation impl | **Modify** — implement monthly bucketing |
| `zero-core/.../categories/detail/CategoryDetailViewModel.kt` | Screen state | **Modify** — add `TrendPoint` + `trend` to `State` |
| `zero-core/.../categories/detail/DefaultCategoryDetailViewModel.kt` | State plumbing | **Modify** — collect `queryMonthlyTrend` |
| `zero-core/.../categories/detail/CategoryDetailViewProvider.kt` | View | **Modify** — add `TrendCard`, place in `hero` slot |
| `zero-core/src/main/res/values/strings.xml` | Strings | **Modify** — add `category_detail_trend_title` |
| `zero-core/.../categories/DefaultCategorySpendingUseCaseTest.kt` | Use-case test | **Create** — bucketing test |
| `zero-core/.../categories/detail/DefaultCategoryDetailViewModelTest.kt` | VM test | **Modify** — stub + assert `trend` |

---

## Task 1: Data — monthly trend on `CategorySpendingUseCase`

**Analog:** `queryForCategory` / `aggregateForCategory` already in `DefaultCategorySpendingUseCase.kt` — follow their transfer-exclusion and `convertToPrimary` handling exactly. Window resolution mirrors the existing `Period.resolve()`.

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategorySpendingUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCaseTest.kt` (create)

- [ ] **Step 1: Add the contract to `CategorySpendingUseCase`**

Add inside the interface (alongside `queryForCategory`) and a `MonthlySpending` type next to `CategorySpending`:

```kotlin
/** [months] monthly buckets for a category, oldest → newest, zero-filled for empty months. */
fun queryMonthlyTrend(id: Id.Known, months: Int): Flow<List<MonthlySpending>>

data class MonthlySpending(
    val month: LocalDate, // first day of the month
    val totalAmount: Amount,
)
```

Add to `Noop`:

```kotlin
override fun queryMonthlyTrend(id: Id.Known, months: Int): Flow<List<MonthlySpending>> = emptyFlow()
```

- [ ] **Step 2: Write the failing test**

Create `DefaultCategorySpendingUseCaseTest.kt`. Mock `TransactionRepository`, `CurrencyConvertUseCase`, `Clock` (fixed `2026-04-15`), `ZoneProvider` (UTC). Stub `convertToPrimary` to return its input amount. Stub `transactionRepository.query(any())` to return a flow of two `Expense` transactions dated in different months (e.g. Feb and Apr 2026). Assert the emitted list has 6 buckets (Nov 2025 → Apr 2026), with the right months summed and the rest `Amount.zero()`.

```kotlin
@Test
fun `queryMonthlyTrend buckets spend by month, zero-filling empty months`() = runTest {
    whenever(currencyConvertUseCase.convertToPrimary(any(), any()))
        .thenAnswer { it.arguments[0] as Amount }
    whenever(transactionRepository.query(any())).thenReturn(
        flowOf(
            listOf(
                expense(amount = "100", date = LocalDate(2026, 2, 10)),
                expense(amount = "40", date = LocalDate(2026, 4, 3)),
                expense(amount = "60", date = LocalDate(2026, 4, 20)),
            ),
        ),
    )

    val result = useCase.queryMonthlyTrend(Id.Known("cat1"), months = 6).first()

    assertEquals(6, result.size)
    assertEquals(LocalDate(2025, 11, 1), result.first().month)
    assertEquals(LocalDate(2026, 4, 1), result.last().month)
    assertEquals(Amount.of(BigDecimal("100")), result[3].totalAmount) // Feb
    assertEquals(Amount.of(BigDecimal("100")), result.last().totalAmount) // Apr (40+60)
    assertEquals(Amount.zero(), result.first().totalAmount) // Nov
}
```

> The `expense(...)` helper builds a `TransactionRepository.Transaction.Expense` with the given amount/date — copy the constructor shape from `TransactionRepository.Transaction.Expense` in `zero-api`. Confirm `Amount.of(BigDecimal)` is the construction call (grep `Amount.kt`; use whatever factory the existing tests use, e.g. `DefaultCategoryDetailViewModelTest` builds amounts via `Amount.of(BigDecimal(...))`).

- [ ] **Step 3: Run test, verify it fails**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategorySpendingUseCaseTest*"`
Expected: FAIL (method not implemented / returns empty).

- [ ] **Step 4: Implement `queryMonthlyTrend`**

In `DefaultCategorySpendingUseCase`:

```kotlin
override fun queryMonthlyTrend(id: Id.Known, months: Int): Flow<List<CategorySpendingUseCase.MonthlySpending>> {
    val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
    val firstMonth = LocalDate(today.year, today.month, 1).minus(months - 1, DateTimeUnit.MONTH)
    val buckets = (0 until months).map { firstMonth.plus(it, DateTimeUnit.MONTH) }
    return transactionRepository
        .query(TransactionRepository.Criteria.ForCategoryBetween(id, firstMonth, today))
        .flatMapLatest { transactions -> flow { emit(bucketByMonth(transactions, buckets)) } }
}

private suspend fun bucketByMonth(
    transactions: List<TransactionRepository.Transaction>,
    buckets: List<LocalDate>,
): List<CategorySpendingUseCase.MonthlySpending> {
    val totals = buckets.associateWith { Amount.zero() }.toMutableMap()
    for (tx in transactions) {
        if (tx is TransactionRepository.Transaction.Transfer) continue
        val key = LocalDate(tx.dateTime.year, tx.dateTime.month, 1)
        val converted = currencyConvertUseCase.convertToPrimary(tx.amount, tx.currencyId)
        totals[key]?.let { totals[key] = it + converted }
    }
    return buckets.map { CategorySpendingUseCase.MonthlySpending(it, totals.getValue(it)) }
}
```

> Imports already present in the file: `kotlinx.datetime.minus`, `DateTimeUnit`, `flatMapLatest`, `flow`. Add `kotlinx.datetime.plus` for `firstMonth.plus(...)`.

- [ ] **Step 5: Run test, verify it passes**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategorySpendingUseCaseTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategorySpendingUseCase.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCaseTest.kt
git commit -m "feat(categories): monthly trend query on CategorySpendingUseCase"
```

---

## Task 2: State — `trend` on `CategoryDetailViewModel`

**Analog:** the existing `largestAmount` collector block in `DefaultCategoryDetailViewModel.attach()` — add a sibling `launch { … }` with the same `collectLatest { … mutableState.update { it.copy(...) } }` shape.

**Files:**
- Modify: `zero-core/.../categories/detail/CategoryDetailViewModel.kt`
- Modify: `zero-core/.../categories/detail/DefaultCategoryDetailViewModel.kt`
- Test: `zero-core/.../categories/detail/DefaultCategoryDetailViewModelTest.kt`

- [ ] **Step 1: Add `TrendPoint` + `trend` to `State`**

In `CategoryDetailViewModel.kt`, add to the interface body and `State`:

```kotlin
data class TrendPoint(
    val month: LocalDate,
    val amount: Amount,
    val isCurrent: Boolean,
)
```

Add field to `State` (default empty): `val trend: List<TrendPoint> = emptyList(),`

- [ ] **Step 2: Write the failing test**

In `DefaultCategoryDetailViewModelTest`, add to `setUp()` so existing tests don't break:

```kotlin
whenever(categorySpendingUseCase.queryMonthlyTrend(any(), any())).thenReturn(flowOf(emptyList()))
```

Then a new test:

```kotlin
@Test
fun `state maps monthly trend with current month flagged`() = runTest {
    whenever(categorySpendingUseCase.queryMonthlyTrend(categoryId, 6)).thenReturn(
        flowOf(
            listOf(
                CategorySpendingUseCase.MonthlySpending(LocalDate(2026, 3, 1), Amount.of(BigDecimal("280"))),
                CategorySpendingUseCase.MonthlySpending(LocalDate(2026, 4, 1), Amount.of(BigDecimal("290"))),
            ),
        ),
    )
    viewModel.attach().use {
        runCurrent()
        val trend = viewModel.state.first().trend
        assertEquals(2, trend.size)
        assertEquals(false, trend.first().isCurrent)
        assertEquals(true, trend.last().isCurrent)
        assertEquals(Amount.of(BigDecimal("290")), trend.last().amount)
    }
}
```

> Use whatever `viewModel` construction / `attach().use { runCurrent() }` pattern the existing tests in this file already use — match it exactly.

- [ ] **Step 3: Run test, verify it fails**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategoryDetailViewModelTest*"`
Expected: FAIL (`trend` empty).

- [ ] **Step 4: Add the collector**

In `DefaultCategoryDetailViewModel.attach()`, add a sibling `launch` inside the outer `coroutineScope.launch { … }`:

```kotlin
launch {
    categorySpendingUseCase.queryMonthlyTrend(categoryId, MONTHS_OF_TREND).collectLatest { months ->
        val lastIndex = months.lastIndex
        mutableState.update { state ->
            state.copy(
                trend = months.mapIndexed { index, month ->
                    CategoryDetailViewModel.TrendPoint(
                        month = month.month,
                        amount = month.totalAmount,
                        isCurrent = index == lastIndex,
                    )
                },
            )
        }
    }
}
```

Add `private const val MONTHS_OF_TREND = 6` at file scope (top-level, below imports).

- [ ] **Step 5: Run test, verify it passes**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategoryDetailViewModelTest*"`
Expected: PASS (both new and existing tests).

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModel.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModelTest.kt
git commit -m "feat(categories): expose 6-month trend in CategoryDetail state"
```

---

## Task 3: View — `TrendCard` in `CategoryDetailViewProvider`

**Analog:** `HeroCard` in the same file (card padding, `surfaceLowest`/tinted backgrounds, `Text` styles) + the gallery's `CategoryTrend()` (`zero-ui/.../chart/ChartsGalleryScreen.kt:91-106`) for the `BarChartData` build. The `.map` that builds `BarChartData` is presentation mapping (no filter/sort/aggregate) — same as `CategoryTrend()`/`flowBars()`; it does not trip `StateCollectionDerivationDetector`.

**Files:**
- Modify: `zero-core/.../categories/detail/CategoryDetailViewProvider.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the string resource**

In `zero-core/src/main/res/values/strings.xml`, next to the other `category_detail_*` entries:

```xml
<string name="category_detail_trend_title">6-month trend</string>
```

- [ ] **Step 2: Add the `TrendCard` composable**

In `CategoryDetailViewProvider.kt`, add (imports: `com.hluhovskyi.zero.ui.chart.BarChart`, `BarChartData`, `BarGroup`, `BarValue`; `java.time.format.DateTimeFormatter`/`Locale` already imported):

```kotlin
@Composable
private fun TrendCard(
    state: CategoryDetailViewModel.State,
    colorScheme: UiColorScheme,
    amountFormatter: AmountFormatter,
) {
    if (state.trend.isEmpty()) return
    val accent = colorScheme.primary
    val dim = colorScheme.primary.copy(alpha = 0.4f)
    val data = BarChartData(
        state.trend.map { point ->
            BarGroup(
                label = point.month
                    .toJavaLocalDate()
                    .format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())),
                bars = listOf(
                    BarValue(
                        value = point.amount.value.toFloat(),
                        color = if (point.isCurrent) accent else dim,
                    ),
                ),
                topLabel = amountFormatter.format(point.amount, state.currencySymbol),
            )
        },
    )
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.surfaceLowest)
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.category_detail_trend_title).uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.0.sp,
            ),
        )
        Spacer(Modifier.size(14.dp))
        BarChart(data, Modifier.fillMaxWidth(), barCornerRadius = 6.dp, barWidth = 28.dp)
    }
}
```

> Confirm `ZeroTheme.colors.surfaceLowest` exists (grep `ZeroTheme`/`UiColorScheme`); if the token is named differently use the nearest card-surface token used elsewhere in this file.

- [ ] **Step 3: Place the card in the `hero` slot**

In `View()`, change the `hero =` argument so the tinted card and the trend card stack and collapse together:

```kotlin
hero = {
    Column {
        HeroCard(state, colorScheme, imageLoader, amountFormatter)
        TrendCard(state, colorScheme, amountFormatter)
    }
},
```

(The transaction list stays the black-box `content = { transactionComponent.AttachWithView() }`; do not inject the chart into it — sub-components are opaque per `docs/agents/architecture.md`.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt \
        zero-core/src/main/res/values/strings.xml
git commit -m "feat(categories): render 6-month trend chart on CategoryDetail"
```

---

## Task 4: Verification

Per `AGENTS.md` (UI Validation): compilation is **not** validation. Run the full gate, then verify on device.

- [ ] **Step 1: Build gates in one invocation**

Run: `./gradlew spotlessApply testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL. If `spotlessApply` reformats files, `git add` + commit them.

- [ ] **Step 2: UI inspection on device**

Invoke `zero-project:android-ui-inspector`. Navigate to a category with several months of transactions, open its detail screen, and confirm:
- The "6-MONTH TREND" card renders below the tinted hero, above the transaction list.
- 6 bars, month labels (`Nov`…`Apr`-style) below, `$` amounts above; the last bar full-opacity in the category color, the prior five dimmed.
- Scrolling the transaction list collapses the hero + trend card together.
- A category with sparse history still renders the chart frame (zero-filled bars / dashed baseline from `BarChart`), not a blank box.

Capture a screenshot for the PR.

- [ ] **Step 3: Final commit if spotless changed anything**

```bash
git add -A && git commit -m "style: spotlessApply" --allow-empty
```

---

## Self-Review Notes

- **Spec coverage:** trend card title ✔ (Task 3), 6 monthly bars oldest→newest ✔ (Task 1 buckets + Task 2 order), value-on-top + month label ✔ (`topLabel`/`label`), current-month emphasis ✔ (`isCurrent` → accent vs dim), category-tinted bars ✔ (`colorScheme.primary`), placement below hero ✔ (hero slot Column), sparse/empty handling ✔ (zero-fill + `BarChart` empty states).
- **No DI change:** `categorySpendingUseCase` is already a `DefaultCategoryDetailViewModel` constructor param; the new method needs no `CategoryDetailComponent` edit.
- **Type consistency:** `MonthlySpending(month, totalAmount)` (use case) → `TrendPoint(month, amount, isCurrent)` (VM) → `BarGroup/BarValue` (view). `queryMonthlyTrend(id, months)` signature identical across api/impl/Noop/test stubs.
- **Out of scope (deferred, not in this plan):** the design's hero trend-arrow (`▲11%`) and swapping the `LARGEST` stat for `VS PRIOR` — the named ask is the chart; the hero already matches the design otherwise. Note these in the PR body as possible follow-ups.
