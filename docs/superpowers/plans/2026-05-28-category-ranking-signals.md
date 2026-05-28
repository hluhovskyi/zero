# Category Ranking Signals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire account / date (month) / amount signals into the existing `CategoriesQueryUseCase.queryRanked` so the transaction-edit category picker ranks contextually instead of by global frequency × recency alone.

**Architecture:** Three new reactive Room queries (per-account, per-month, average-amount) feed three multipliers applied multiplicatively on top of the existing `count × recencyDecay` base score. Signals fold into a `SignalState` via `runningFold`; `flatMapLatest` re-subscribes the inner `combine` when any signal changes. Signals are sourced from `DefaultTransactionEditUseCase.mutableState` via `.map { … }.distinctUntilChanged()` then merged.

**Tech Stack:** Kotlin coroutines (`Flow`, `runningFold`, `flatMapLatest`, `merge`), Room + `kotlinx-datetime`, injected `Clock` / `ZoneProvider`, `BigDecimal`. Existing test infra: JUnit4 + Mockito-Kotlin.

**Spec:** `docs/superpowers/specs/2026-05-28-category-ranking-signals-design.md`

**Reference patterns** (read before modifying):
- DAO + Criteria pattern → `docs/agents/data-layer.md`
- Existing `Criteria.CategoryUsageStatistics` and `selectCategoryUsageStatistic` — clone for new queries
- Existing `RankSignal.AccountChanged` / `DateChanged` in `CategoriesQueryUseCase.kt` — same shape for new variant
- Existing 2 tests in `DefaultCategoriesQueryUseCaseTest.kt` — same setup helpers for new tests

---

## Task 1: Add API types (zero-api)

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`

### Step 1: Add `RankSignal.AmountChanged`

In `CategoriesQueryUseCase.kt`, inside the `sealed class RankSignal`, add the third variant alongside the existing two:

```kotlin
import java.math.BigDecimal
// ... existing imports ...

sealed class RankSignal {
    data class AccountChanged(val accountId: Id.Known?) : RankSignal()
    data class DateChanged(val date: LocalDate?) : RankSignal()
    data class AmountChanged(val amount: BigDecimal?) : RankSignal()
}
```

### Step 2: Add `CategoryAmountStatistic` data class

In `TransactionRepository.kt`, alongside the existing `CategoryUsageStatistic` and `CategorySpendingStatistic`:

```kotlin
data class CategoryAmountStatistic(
    val categoryId: Id.Known,
    val averageAmount: BigDecimal,
)
```

Add `import java.math.BigDecimal` if not already present.

### Step 3: Add 3 new `Criteria` variants

Inside `sealed interface Criteria<T>` in `TransactionRepository.kt`, alongside the existing `CategoryUsageStatistics`:

```kotlin
data class CategoryUsageStatisticsByAccount(val accountId: Id.Known)
    : Criteria<List<CategoryUsageStatistic>>
data class CategoryUsageStatisticsByMonth(val month: Int)
    : Criteria<List<CategoryUsageStatistic>>
class CategoryAmountStatistics : Criteria<List<CategoryAmountStatistic>>
```

### Step 4: Verify zero-api compiles

Run: `./gradlew :zero-api:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. `RoomTransactionRepository` and `RoomTransactionRepository`'s `when` branch will still compile (sealed `when` exhaustiveness is enforced where it's used; the existing repository code does not break because new variants are subtypes of `Criteria`).

If a `when` exhaustiveness warning surfaces in `RoomTransactionRepository`, leave it — Task 2 adds the branches.

### Step 5: Commit

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt \
        zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt
git commit -m "feat(api): RankSignal.AmountChanged + per-account/month/amount Criteria"
```

---

## Task 2: Add DAO queries + repository dispatch (zero-database)

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

### Step 1: Add three DAO queries to `TransactionRoom`

Add these three `@Query` methods inside the `@Dao` interface, near the existing `selectCategoryUsageStatistic`:

```kotlin
@Query(
    """
    SELECT categoryId,
           COUNT(*) as transactionCount,
           MAX(enteredDateTime) as lastUsedDateTime
    FROM TransactionEntity
    WHERE userId = :userId
      AND categoryId IS NOT NULL
      AND accountId = :accountId
      AND deletedAt IS NULL
    GROUP BY categoryId
""",
)
fun selectCategoryUsageStatisticByAccount(
    userId: String,
    accountId: String,
): Flow<List<CategoryUsageStatistic>>

@Query(
    """
    SELECT categoryId,
           COUNT(*) as transactionCount,
           MAX(enteredDateTime) as lastUsedDateTime
    FROM TransactionEntity
    WHERE userId = :userId
      AND categoryId IS NOT NULL
      AND strftime('%m', enteredDateTime) = :month
      AND deletedAt IS NULL
    GROUP BY categoryId
""",
)
fun selectCategoryUsageStatisticByMonth(
    userId: String,
    month: String,
): Flow<List<CategoryUsageStatistic>>

@Query(
    """
    SELECT categoryId,
           AVG(ABS(amount_value)) AS averageAmount
    FROM TransactionEntity
    WHERE userId = :userId
      AND categoryId IS NOT NULL
      AND deletedAt IS NULL
    GROUP BY categoryId
""",
)
fun selectCategoryAmountStatistic(userId: String): Flow<List<CategoryAmountStatistic>>
```

### Step 2: Add the `CategoryAmountStatistic` projection class

Create `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryAmountStatistic.kt` modeled exactly on the existing `CategoryUsageStatistic.kt` in the same directory:

```kotlin
package com.hluhovskyi.zero.transactions

import java.math.BigDecimal

internal data class CategoryAmountStatistic(
    val categoryId: String,
    val averageAmount: BigDecimal,
)
```

`categoryId` is `String` here (the raw column value); the repo layer wraps it in `Id.Known` when converting to the API type. Follow `CategoryUsageStatistic` as the structural template.

### Step 3: Wire 3 new branches in `RoomTransactionRepository.query()`

Inside the existing `when (criteria)` block (around line 38 — the one with `Criteria.CategoryUsageStatistics`), add three branches following the same `currentUserId.take(1).flatMapConcat { userId -> … }` shape:

```kotlin
is TransactionRepository.Criteria.CategoryUsageStatisticsByAccount -> transactionRoom()
    .selectCategoryUsageStatisticByAccount(userId.value, criteria.accountId.value)
    .map { entities ->
        entities.map { entity ->
            TransactionRepository.CategoryUsageStatistic(
                categoryId = Id.Known(entity.categoryId),
                transactionCount = entity.transactionCount,
                lastUsedDateTime = entity.lastUsedDateTime,
            )
        }
    }

is TransactionRepository.Criteria.CategoryUsageStatisticsByMonth -> transactionRoom()
    .selectCategoryUsageStatisticByMonth(userId.value, "%02d".format(criteria.month))
    .map { entities ->
        entities.map { entity ->
            TransactionRepository.CategoryUsageStatistic(
                categoryId = Id.Known(entity.categoryId),
                transactionCount = entity.transactionCount,
                lastUsedDateTime = entity.lastUsedDateTime,
            )
        }
    }

is TransactionRepository.Criteria.CategoryAmountStatistics -> transactionRoom()
    .selectCategoryAmountStatistic(userId.value)
    .map { entities ->
        entities.map { entity ->
            TransactionRepository.CategoryAmountStatistic(
                categoryId = Id.Known(entity.categoryId),
                averageAmount = entity.averageAmount,
            )
        }
    }
```

Read the existing `CategoryUsageStatistics` branch (lines 61–72 in master) to ensure the wrapping shape (e.g. `take(1).flatMapConcat`) matches — clone it exactly. The structural template is **the existing `CategoryUsageStatistics` branch**.

### Step 4: Build the module

Run: `./gradlew :zero-database:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. Room annotation processor will emit method bindings for the new `@Query` methods; if it fails, the SQL is the culprit.

If Room complains about projection column names — adjust the SQL aliases to exactly match the `CategoryAmountStatisticRow` field names.

### Step 5: Commit

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/
git commit -m "feat(db): per-account/month/amount category stats queries"
```

---

## Task 3: Rewrite `DefaultCategoriesQueryUseCase` (zero-core)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`

This is the central change. We rewrite `queryRanked` to fold signals into state, query the relevant per-signal stats reactively, and multiply per-category scores.

### Step 1: Write a failing test for the account-boost signal

Append to `DefaultCategoriesQueryUseCaseTest.kt`:

```kotlin
@Test
fun `queryRanked boosts categories used with selected account`() = runTest {
    val catA = CategoryRepository.Category(
        id = Id.Known("a"), parentCategoryId = Id.Unknown,
        name = "A", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    val catB = CategoryRepository.Category(
        id = Id.Known("b"), parentCategoryId = Id.Unknown,
        name = "B", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(catA, catB)))

    // Equal global usage — base score should be equal.
    val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate),
                TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 3, recentDate),
            ),
        ),
    )
    // Account-specific: only catB used with this account
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatisticsByAccount>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 2, recentDate)),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryAmountStatistics>(),
            any(),
        ),
    ).thenReturn(flowOf(emptyList()))

    val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
        CategoriesQueryUseCase.RankSignal.AccountChanged(Id.Known("acc-1")),
    )

    val result = createUseCase().queryRanked(signals).first()
    assertEquals(Id.Known("b"), result[0].id)
    assertEquals(Id.Known("a"), result[1].id)
}
```

### Step 2: Run the test, confirm it fails

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategoriesQueryUseCaseTest.queryRanked boosts categories used with selected account*" 2>&1 | tail -25`
Expected: FAIL — the current implementation ignores signals.

### Step 3: Rewrite `queryRanked` to be signal-driven

Replace the entire body of `DefaultCategoriesQueryUseCase.kt` between (and including) the `override fun queryRanked` declaration and the existing `rankCategories(...)` private function with the signal-driven version below. Keep `queryAll`, `queryById`, `resolve`, and the `companion object` unchanged.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
override fun queryRanked(
    signals: Flow<CategoriesQueryUseCase.RankSignal>,
): Flow<List<CategoriesQueryUseCase.Category>> {
    val signalState = signals.runningFold(SignalState()) { state, signal ->
        when (signal) {
            is CategoriesQueryUseCase.RankSignal.AccountChanged ->
                state.copy(accountId = signal.accountId)
            is CategoriesQueryUseCase.RankSignal.DateChanged ->
                state.copy(date = signal.date)
            is CategoriesQueryUseCase.RankSignal.AmountChanged ->
                state.copy(amount = signal.amount)
        }
    }

    return signalState.flatMapLatest { state ->
        val accountStatsFlow = state.accountId?.let {
            transactionRepository.query(
                TransactionRepository.Criteria.CategoryUsageStatisticsByAccount(it),
            )
        } ?: flowOf(emptyList())

        val monthStatsFlow = state.date?.let {
            transactionRepository.query(
                TransactionRepository.Criteria.CategoryUsageStatisticsByMonth(it.monthNumber),
            )
        } ?: flowOf(emptyList())

        val amountStatsFlow = transactionRepository.query(
            TransactionRepository.Criteria.CategoryAmountStatistics(),
        )

        combine(
            queryAll,
            transactionRepository.query(TransactionRepository.Criteria.CategoryUsageStatistics()),
            accountStatsFlow,
            monthStatsFlow,
            amountStatsFlow,
        ) { categories, globalStats, accountStats, monthStats, amountStats ->
            rankCategories(categories, globalStats, accountStats, monthStats, amountStats, state.amount)
        }
    }
}

private fun rankCategories(
    categories: List<CategoriesQueryUseCase.Category>,
    globalStats: List<TransactionRepository.CategoryUsageStatistic>,
    accountStats: List<TransactionRepository.CategoryUsageStatistic>,
    monthStats: List<TransactionRepository.CategoryUsageStatistic>,
    amountStats: List<TransactionRepository.CategoryAmountStatistic>,
    enteredAmount: BigDecimal?,
): List<CategoriesQueryUseCase.Category> {
    val globalById = globalStats.associateBy { it.categoryId }
    val accountById = accountStats.associateBy { it.categoryId }
    val monthById = monthStats.associateBy { it.categoryId }
    val amountById = amountStats.associateBy { it.categoryId }
    val nowInstant = clock.now()
    val timeZone = zoneProvider.timeZone()

    val (used, unused) = categories.partition { globalById.containsKey(it.id) }

    val scored = used
        .map { category ->
            val globalStat = globalById.getValue(category.id)
            val daysSinceLastUse = (nowInstant - globalStat.lastUsedDateTime.toInstant(timeZone))
                .inWholeDays.toDouble()
            val recencyDecay = exp(-daysSinceLastUse / DECAY_PERIOD_DAYS)
            val baseScore = globalStat.transactionCount * recencyDecay

            val accountMultiplier = accountById[category.id]?.let { accountStat ->
                1.0 + accountStat.transactionCount.toDouble() / globalStat.transactionCount
            } ?: 1.0

            val monthMultiplier = monthById[category.id]?.let { monthStat ->
                1.0 + MONTH_WEIGHT * monthStat.transactionCount.toDouble() / globalStat.transactionCount
            } ?: 1.0

            val amountMultiplier = amountProximityMultiplier(enteredAmount, amountById[category.id])

            val score = baseScore * accountMultiplier * monthMultiplier * amountMultiplier
            category to score
        }
        .sortedByDescending { it.second }
        .map { it.first }

    val alphabetical = unused.sortedBy { it.name }
    return scored + alphabetical
}

private fun amountProximityMultiplier(
    enteredAmount: BigDecimal?,
    amountStat: TransactionRepository.CategoryAmountStatistic?,
): Double {
    if (enteredAmount == null || amountStat == null) return 1.0
    val entered = enteredAmount.toDouble()
    val average = amountStat.averageAmount.toDouble()
    if (entered <= 0.0 || average <= 0.0) return 1.0
    val logRatio = ln(entered / average)
    val proximity = exp(-(logRatio * logRatio) / (2.0 * AMOUNT_SIGMA * AMOUNT_SIGMA))
    return 1.0 + AMOUNT_WEIGHT * proximity
}

private data class SignalState(
    val accountId: Id.Known? = null,
    val date: LocalDate? = null,
    val amount: BigDecimal? = null,
)
```

Update imports at the top:

```kotlin
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import kotlin.math.exp
import kotlin.math.ln
```

Update the `companion object` block:

```kotlin
private companion object {
    const val DECAY_PERIOD_DAYS = 30.0
    const val MONTH_WEIGHT = 0.5
    const val AMOUNT_WEIGHT = 0.75
    const val AMOUNT_SIGMA = 1.0
}
```

### Step 4: Verify account-boost test passes

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategoriesQueryUseCaseTest.queryRanked boosts categories used with selected account*" 2>&1 | tail -25`
Expected: PASS.

### Step 5: Add month-boost test

```kotlin
@Test
fun `queryRanked boosts categories used in same month`() = runTest {
    val catA = CategoryRepository.Category(
        id = Id.Known("a"), parentCategoryId = Id.Unknown,
        name = "A", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    val catB = CategoryRepository.Category(
        id = Id.Known("b"), parentCategoryId = Id.Unknown,
        name = "B", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(catA, catB)))

    val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate),
                TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 3, recentDate),
            ),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatisticsByMonth>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate)),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryAmountStatistics>(),
            any(),
        ),
    ).thenReturn(flowOf(emptyList()))

    val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
        CategoriesQueryUseCase.RankSignal.DateChanged(LocalDate(2024, 6, 15)),
    )

    val result = createUseCase().queryRanked(signals).first()
    assertEquals(Id.Known("a"), result[0].id)
    assertEquals(Id.Known("b"), result[1].id)
}
```

Add `import kotlinx.datetime.LocalDate` (if not already imported).

### Step 6: Add amount-boost test

```kotlin
@Test
fun `queryRanked boosts categories close to entered amount on log scale`() = runTest {
    val catSmall = CategoryRepository.Category(
        id = Id.Known("small"), parentCategoryId = Id.Unknown,
        name = "Small", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    val catLarge = CategoryRepository.Category(
        id = Id.Known("large"), parentCategoryId = Id.Unknown,
        name = "Large", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(catSmall, catLarge)))

    val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryUsageStatistic(Id.Known("small"), 2, recentDate),
                TransactionRepository.CategoryUsageStatistic(Id.Known("large"), 2, recentDate),
            ),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryAmountStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryAmountStatistic(Id.Known("small"), BigDecimal("6.00")),
                TransactionRepository.CategoryAmountStatistic(Id.Known("large"), BigDecimal("500.00")),
            ),
        ),
    )

    val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
        CategoriesQueryUseCase.RankSignal.AmountChanged(BigDecimal("5.00")),
    )

    val result = createUseCase().queryRanked(signals).first()
    assertEquals(Id.Known("small"), result[0].id)
    assertEquals(Id.Known("large"), result[1].id)
}
```

Add `import java.math.BigDecimal` to the test file.

### Step 7: Add combined-signal test

```kotlin
@Test
fun `queryRanked combines all signals multiplicatively`() = runTest {
    val catMatch = CategoryRepository.Category(
        id = Id.Known("match"), parentCategoryId = Id.Unknown,
        name = "Match", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    val catBase = CategoryRepository.Category(
        id = Id.Known("base"), parentCategoryId = Id.Unknown,
        name = "Base", iconId = Id.Unknown, colorId = Id.Unknown,
    )
    whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
        .thenReturn(flowOf(listOf(catMatch, catBase)))

    val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
    // catBase has HIGHER base usage to confirm signals can flip the ranking.
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate),
                TransactionRepository.CategoryUsageStatistic(Id.Known("base"), 3, recentDate),
            ),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatisticsByAccount>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate)),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryUsageStatisticsByMonth>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate)),
        ),
    )
    whenever(
        transactionRepository.query(
            any<TransactionRepository.Criteria.CategoryAmountStatistics>(),
            any(),
        ),
    ).thenReturn(
        flowOf(
            listOf(
                TransactionRepository.CategoryAmountStatistic(Id.Known("match"), BigDecimal("10.00")),
                TransactionRepository.CategoryAmountStatistic(Id.Known("base"), BigDecimal("500.00")),
            ),
        ),
    )

    val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
        CategoriesQueryUseCase.RankSignal.AccountChanged(Id.Known("acc-1")),
        CategoriesQueryUseCase.RankSignal.DateChanged(LocalDate(2024, 6, 15)),
        CategoriesQueryUseCase.RankSignal.AmountChanged(BigDecimal("10.00")),
    )

    val result = createUseCase().queryRanked(signals).first()
    assertEquals(Id.Known("match"), result[0].id)
    assertEquals(Id.Known("base"), result[1].id)
}
```

### Step 8: Run the full UseCase test class

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultCategoriesQueryUseCaseTest*" 2>&1 | tail -30`
Expected: 6 tests, all passing — 2 existing + 4 new.

If the 2 existing tests fail, the `flowOf(emptyList())` defaults for the new `Criteria` variants are missing in the existing setup. Add `whenever(...).thenReturn(flowOf(emptyList()))` for `CategoryAmountStatistics` (always queried) in the existing tests.

### Step 9: Commit

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt
git commit -m "feat(core): signal-driven multiplicative category ranking"
```

---

## Task 4: Wire signals from `DefaultTransactionEditUseCase`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

### Step 1: Locate the existing `queryRanked(emptyFlow())` call

In `DefaultTransactionEditUseCase.kt`, find the call (around line 398). Replace `emptyFlow()` with a merged signals flow derived from `mutableState`.

### Step 2: Build the merged signals flow

Just before the `categoriesQueryUseCase.queryRanked(...)` call, add:

```kotlin
val accountSignals = mutableState
    .map { it.selectedAccount?.id }
    .distinctUntilChanged()
    .map { CategoriesQueryUseCase.RankSignal.AccountChanged(it) }

val dateSignals = mutableState
    .map { it.localDateTime?.date }
    .distinctUntilChanged()
    .map { CategoriesQueryUseCase.RankSignal.DateChanged(it) }

val amountSignals = mutableState
    .map { it.amount.toBigDecimalOrNull()?.takeIf { value -> value > BigDecimal.ZERO } }
    .distinctUntilChanged()
    .map { CategoriesQueryUseCase.RankSignal.AmountChanged(it) }

val signals = merge(accountSignals, dateSignals, amountSignals)

categoriesQueryUseCase.queryRanked(signals)
    // ... existing .map { categories -> ... }.collectLatest { ... } block unchanged
```

### Step 3: Add imports

Add to the top of the file:

```kotlin
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import java.math.BigDecimal
```

Remove `import kotlinx.coroutines.flow.emptyFlow` if unused after this change.

### Step 4: Compile the module

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

### Step 5: Build the app to confirm full graph compiles

Run: `./gradlew assembleDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL.

### Step 6: Commit

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
git commit -m "feat(edit): wire account/date/amount signals into category ranking"
```

---

## Final Verification

Run all in one fail-fast invocation:

```bash
./gradlew :zero-core:testDebugUnitTest :zero-database:assembleDebug :app:lintDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, no new lint warnings.

Then UI verification per `/lets-do` Step 5: launch the app, create or edit a transaction with different account / date / amount values, confirm the category list re-ranks reactively. Use `zero-project:android-ui-inspector` to capture the picker state.

---

## Out of scope

- Tuning weights based on usage data (defer; PR #12 values stand).
- Persisting `SignalState` between sessions (signals reset per `attach`).
- Debouncing amount keystrokes beyond `distinctUntilChanged`.
- Documenting ranking internals in `docs/agents/category-ranking.md` (per saved feedback: contained features don't need a cross-module agent doc).
