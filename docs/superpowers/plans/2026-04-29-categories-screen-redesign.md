# Categories Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the categories list screen to use white cards matching the design system and surface per-category spending stats (amount, transaction count, % of total, relative bar) for the current month.

**Architecture:** A new `CategorySpendingBetween` criteria is added to `TransactionRepository` — the DB aggregates totals per `(categoryId, currencyId)` with SQL `GROUP BY` so nothing is fetched wholesale. `CategorySpendingUseCase` (interface in `zero-api`, impl in `zero-core`) converts each row to the primary currency and collapses to per-category totals. `CategoryViewModel.Spending` is a sealed class (`Active` | `None`) with raw data only — no presentation fields. `CategoryViewProvider` computes `barFraction` and `percentOfTotal` from the list itself.

**Tech Stack:** Kotlin, Jetpack Compose, Room (SQLite `GROUP BY`), Dagger, Coroutines + Flow, `kotlinx-datetime`, `CurrencyConvertUseCase`.

---

## Current state — already done, do not redo

`CategoryViewProvider.kt` was partially edited before this plan. It has:
- `"Categories"` title header (22sp ExtraBold Primary)
- White card rows (`SurfaceContainerLowest` bg, 16dp radius)
- `weight(1f)` on the name `Text`, `sizeIn` + `aspectRatio` on icon, 40dp `CategoryIconView`

All other files are at their original unmodified state.

---

## File Map

| File | Action |
|------|--------|
| `zero-api/…/transactions/TransactionRepository.kt` | Add `CategorySpendingBetween` criteria + `CategorySpendingStatistic` result type |
| `zero-database/…/transactions/CategorySpendingRow.kt` | **Create** — internal Room query result data class |
| `zero-database/…/transactions/TransactionRoom.kt` | Add `@Query` for aggregated spending |
| `zero-database/…/transactions/RoomTransactionRepository.kt` | Handle new criteria in `when` |
| `zero-api/…/categories/CategorySpendingUseCase.kt` | **Create** — interface + `Period` sealed class + `CategorySpending` model |
| `zero-core/…/categories/DefaultCategorySpendingUseCase.kt` | **Create** — uses new criteria, converts currency, collapses by category |
| `zero-core/…/categories/CategoryViewModel.kt` | `Spending` → sealed class `Active(totalAmount, transactionCount)` / `None` |
| `zero-core/…/categories/DefaultCategoryViewModel.kt` | Accept `CategorySpendingUseCase`; combine + sort |
| `zero-core/…/categories/CategoryComponent.kt` | Add `categorySpendingUseCase` + `amountFormatter` to `Dependencies`; wire `Module` |
| `app/…/activity/ActivityComponent.kt` | `@Provides` `DefaultCategorySpendingUseCase` in `Module` |
| `zero-core/…/categories/CategoryViewProvider.kt` | Full redesign: stats rendering, `barFraction`/`percentOfTotal` computed locally |

---

## Task 1 — Add aggregated spending criteria to `TransactionRepository`

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`

- [ ] **Step 1: Add `CategorySpendingBetween` criteria and `CategorySpendingStatistic`**

Add inside `TransactionRepository` — alongside the existing `CategoryUsageStatistic` class and `Criteria` sealed interface:

```kotlin
// Inside Criteria sealed interface, after the existing entries:
data class CategorySpendingBetween(
    val from: kotlinx.datetime.LocalDate,
    val to: kotlinx.datetime.LocalDate,
) : Criteria<List<CategorySpendingStatistic>>
```

```kotlin
// New top-level data class inside TransactionRepository, after CategoryUsageStatistic:
data class CategorySpendingStatistic(
    val categoryId: Id.Known,
    val currencyId: Id.Known,
    val totalAmount: Amount,
    val transactionCount: Int,
)
```

The full updated `TransactionRepository.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface TransactionRepository {

    fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow<Any>()): Flow<T>

    sealed interface Criteria<T> {

        class All : Criteria<List<Transaction>>
        data class ById(val id: Id.Known) : Criteria<Transaction>
        data class After(val dateTime: LocalDateTime) : Criteria<List<Transaction>>
        class CategoryUsageStatistics : Criteria<List<CategoryUsageStatistic>>
        data class Search(val query: String) : Criteria<List<Transaction>>
        data class CategorySpendingBetween(
            val from: LocalDate,
            val to: LocalDate,
        ) : Criteria<List<CategorySpendingStatistic>>
    }

    sealed interface Trigger {
        object LoadAll : Trigger
    }

    suspend fun insert(transaction: Transaction)
    suspend fun insert(transactions: List<Transaction>)
    suspend fun delete(id: Id.Known)

    data class CategoryUsageStatistic(
        val categoryId: Id.Known,
        val transactionCount: Int,
        val lastUsedDateTime: LocalDateTime,
    )

    data class CategorySpendingStatistic(
        val categoryId: Id.Known,
        val currencyId: Id.Known,
        val totalAmount: Amount,
        val transactionCount: Int,
    )

    sealed interface Transaction {

        val id: Id.Known
        val amount: Amount
        val currencyId: Id.Known
        val accountId: Id.Known
        val dateTime: LocalDateTime
        val updatedDateTime: LocalDateTime

        data class Expense(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Income(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Transfer(
            override val id: Id.Known,
            override val amount: Amount,
            override val currencyId: Id.Known,
            override val accountId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val targetAccount: Id.Known,
            val targetAmount: Amount,
        ) : Transaction
    }

    object Noop : TransactionRepository {
        override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
        override suspend fun insert(transaction: Transaction) = Unit
        override suspend fun insert(transactions: List<Transaction>) = Unit
        override suspend fun delete(id: Id.Known) = Unit
    }
}
```

- [ ] **Step 2: Build `zero-api`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-api:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt
git commit -m "feat(transactions): add CategorySpendingBetween criteria to TransactionRepository"
```

---

## Task 2 — Implement the DB query in the Room layer

**Files:**
- Create: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategorySpendingRow.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

`BigDecimal` is stored as `Double` (see `BigDecimalConverters`), so `SUM(amount_value)` is a standard SQLite real-number aggregate — no special handling needed. The query groups by `(categoryId, currencyId)` because transactions carry raw amounts in their own currency; currency conversion happens in the use case.

### 2a — Internal result data class

- [ ] **Step 1: Create `CategorySpendingRow.kt`**

```kotlin
package com.hluhovskyi.zero.transactions

import java.math.BigDecimal

internal data class CategorySpendingRow(
    val categoryId: String,
    val currencyId: String,
    val totalAmount: BigDecimal,
    val transactionCount: Int,
)
```

### 2b — DAO query

- [ ] **Step 2: Add `selectCategorySpendingBetween` to `TransactionRoom.kt`**

Add this method to the existing `TransactionRoom` interface (after `selectCategoryUsageStatistic`):

```kotlin
@Query(
    """
    SELECT categoryId,
           currencyId,
           SUM(amount_value) AS totalAmount,
           COUNT(*) AS transactionCount
    FROM TransactionEntity
    WHERE userId = :userId
      AND categoryId IS NOT NULL
      AND type IN ('EXPENSE', 'INCOME')
      AND deletedAt IS NULL
      AND date(enteredDateTime) >= date(:from)
      AND date(enteredDateTime) <= date(:to)
    GROUP BY categoryId, currencyId
""",
)
fun selectCategorySpendingBetween(
    userId: String,
    from: String,
    to: String,
): Flow<List<CategorySpendingRow>>
```

### 2c — Repository dispatch

- [ ] **Step 3: Handle `CategorySpendingBetween` in `RoomTransactionRepository.kt`**

Add a branch to the `when (criteria)` block inside `query()`, after the `Search` branch:

```kotlin
is TransactionRepository.Criteria.CategorySpendingBetween -> transactionRoom()
    .selectCategorySpendingBetween(
        userId = userId.value,
        from = criteria.from.toString(),
        to = criteria.to.toString(),
    )
    .map { rows ->
        rows.map { row ->
            TransactionRepository.CategorySpendingStatistic(
                categoryId = Id.Known(row.categoryId),
                currencyId = Id.Known(row.currencyId),
                totalAmount = Amount(row.totalAmount),
                transactionCount = row.transactionCount,
            )
        }
    }
```

- [ ] **Step 4: Build `zero-database`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-database:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategorySpendingRow.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
git commit -m "feat(transactions): implement CategorySpendingBetween Room query"
```

---

## Task 3 — Define `CategorySpendingUseCase` API

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategorySpendingUseCase.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface CategorySpendingUseCase {

    fun query(period: Period): Flow<List<CategorySpending>>

    data class CategorySpending(
        val categoryId: Id.Known,
        val totalAmount: Amount,
        val transactionCount: Int,
    )

    sealed class Period {
        object CurrentMonth : Period()
        object CurrentWeek : Period()
        object CurrentYear : Period()
        data class Between(val from: LocalDate, val to: LocalDate) : Period()
    }

    object Noop : CategorySpendingUseCase {
        override fun query(period: Period): Flow<List<CategorySpending>> = emptyFlow()
    }
}
```

- [ ] **Step 2: Build `zero-api`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-api:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategorySpendingUseCase.kt
git commit -m "feat(categories): add CategorySpendingUseCase API with Period"
```

---

## Task 4 — Implement `DefaultCategorySpendingUseCase`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt`

Resolves the `Period` to a `(from, to)` date pair, delegates to `TransactionRepository.Criteria.CategorySpendingBetween`, then converts each `(categoryId, currencyId, amount)` row to the primary currency and collapses into one total per category.

- [ ] **Step 1: Create the file**

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal class DefaultCategorySpendingUseCase(
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : CategorySpendingUseCase {

    override fun query(period: CategorySpendingUseCase.Period): Flow<List<CategorySpendingUseCase.CategorySpending>> {
        val (from, to) = period.resolve()
        return transactionRepository
            .query(TransactionRepository.Criteria.CategorySpendingBetween(from = from, to = to))
            .flatMapLatest { rows -> flow { emit(aggregate(rows)) } }
    }

    private suspend fun aggregate(
        rows: List<TransactionRepository.CategorySpendingStatistic>,
    ): List<CategorySpendingUseCase.CategorySpending> {
        val totals = mutableMapOf<Id.Known, Pair<Amount, Int>>()
        for (row in rows) {
            val converted = currencyConvertUseCase.convertToPrimary(row.totalAmount, row.currencyId)
            val (prev, prevCount) = totals[row.categoryId] ?: Pair(Amount.zero(), 0)
            totals[row.categoryId] = Pair(prev + converted, prevCount + row.transactionCount)
        }
        return totals.map { (categoryId, pair) ->
            CategorySpendingUseCase.CategorySpending(
                categoryId = categoryId,
                totalAmount = pair.first,
                transactionCount = pair.second,
            )
        }
    }

    private fun CategorySpendingUseCase.Period.resolve(): Pair<LocalDate, LocalDate> {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        return when (this) {
            is CategorySpendingUseCase.Period.CurrentMonth ->
                LocalDate(today.year, today.month, 1) to today
            is CategorySpendingUseCase.Period.CurrentWeek ->
                today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY) to today
            is CategorySpendingUseCase.Period.CurrentYear ->
                LocalDate(today.year, 1, 1) to today
            is CategorySpendingUseCase.Period.Between ->
                from to to
        }
    }
}
```

- [ ] **Step 2: Build `zero-core`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-core:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt
git commit -m "feat(categories): implement DefaultCategorySpendingUseCase"
```

---

## Task 5 — Update `CategoryViewModel` state

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt`

`Spending` becomes a sealed class. No `barFraction` or `percentOfTotal` — those are computed in the UI layer.

- [ ] **Step 1: Replace the file**

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel : AttachableActionStateModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryItem) : Action
    }

    data class State(
        val categories: List<CategoryItem> = emptyList(),
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val spending: Spending = Spending.None,
    )

    sealed class Spending {
        data class Active(
            val totalAmount: Amount,
            val transactionCount: Int,
        ) : Spending()

        object None : Spending()
    }
}
```

- [ ] **Step 2: Build `zero-core`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-core:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt
git commit -m "feat(categories): Spending as sealed class Active/None in CategoryViewModel"
```

---

## Task 6 — Update `DefaultCategoryViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
        when (action) {
            is CategoryViewModel.Action.SelectCategory -> coroutineScope.launch(Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                categoriesQueryUseCase.queryAll(),
                categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth),
            ) { categories, spendingList ->
                val spendingById = spendingList.associateBy { it.categoryId }

                val items = categories.map { category ->
                    val spending = spendingById[category.id]
                    CategoryViewModel.CategoryItem(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        colorScheme = category.colorScheme,
                        spending = if (spending != null && spending.totalAmount > 0L) {
                            CategoryViewModel.Spending.Active(
                                totalAmount = spending.totalAmount,
                                transactionCount = spending.transactionCount,
                            )
                        } else {
                            CategoryViewModel.Spending.None
                        },
                    )
                }

                val (active, inactive) = items.partition { it.spending is CategoryViewModel.Spending.Active }
                active.sortedByDescending {
                    (it.spending as CategoryViewModel.Spending.Active).totalAmount.value
                } + inactive.sortedBy { it.name }
            }
                .collectLatest { items ->
                    mutableState.update { it.copy(categories = items) }
                }
        }
    }
}
```

- [ ] **Step 2: Build `zero-core`**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-core:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt
git commit -m "feat(categories): use CategorySpendingUseCase in DefaultCategoryViewModel"
```

---

## Task 7 — Wire `CategorySpendingUseCase` into DI

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`

### 7a — `CategoryComponent`

`ActivityComponent` implements `CategoryComponent.Dependencies`. Adding `categorySpendingUseCase` and `amountFormatter` to `Dependencies` means Dagger will look for bindings in `ActivityComponent`'s graph. Both are provided via `ActivityComponent.Module`.

- [ ] **Step 1: Replace `CategoryComponent.kt`**

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryScope

private const val TAG = "CategoryComponent"

@CategoryScope
@dagger.Component(
    dependencies = [CategoryComponent.Dependencies::class],
    modules = [CategoryComponent.Module::class],
)
abstract class CategoryComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoryQueryUseCase: CategoriesQueryUseCase
        val categorySpendingUseCase: CategorySpendingUseCase
        val amountFormatter: AmountFormatter
    }

    companion object {

        fun queryUseCase(
            categoryRepository: CategoryRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            transactionRepository: TransactionRepository,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategoriesQueryUseCase = DefaultCategoriesQueryUseCase(
            categoryRepository = categoryRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            transactionRepository = transactionRepository,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryComponent.builder()
            .dependencies(dependencies)
            .onCategorySelectedHandler(OnCategorySelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCategorySelectedHandler(handler: OnCategorySelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryScope
        fun viewModel(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            categorySpendingUseCase: CategorySpendingUseCase,
            onCategorySelectedHandler: OnCategorySelectedHandler,
        ): CategoryViewModel = DefaultCategoryViewModel(
            categoriesQueryUseCase = categoriesQueryUseCase,
            categorySpendingUseCase = categorySpendingUseCase,
            onCategorySelectedHandler = onCategorySelectedHandler,
        )

        @Provides
        @CategoryScope
        fun viewProvider(
            viewModel: CategoryViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): ViewProvider = CategoryViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
```

### 7b — `ActivityComponent`

- [ ] **Step 2: Add `@Provides categorySpendingUseCase` to `ActivityComponent.Module`**

Add this method inside `object Module { … }` in `ActivityComponent.kt`, alongside the existing `@Provides` methods. Also add the two imports shown below.

New imports to add at the top of `ActivityComponent.kt`:
```kotlin
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.categories.DefaultCategorySpendingUseCase
```

New `@Provides` method inside `ActivityComponent.Module`:
```kotlin
@Provides
@ActivityScope
fun categorySpendingUseCase(
    transactionRepository: TransactionRepository,
    currencyConvertUseCase: CurrencyConvertUseCase,
    clock: Clock,
    zoneProvider: ZoneProvider,
): CategorySpendingUseCase = DefaultCategorySpendingUseCase(
    transactionRepository = transactionRepository,
    currencyConvertUseCase = currencyConvertUseCase,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

- [ ] **Step 3: Build full project to validate Dagger graph**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :app:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL — Dagger validates the complete graph including the new bindings.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt
git commit -m "feat(categories): wire CategorySpendingUseCase and AmountFormatter into DI"
```

---

## Task 8 — Complete `CategoryViewProvider` UI

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`

Replaces the partial edit. The `barFraction` and `percentOfTotal` are derived here from the raw `Amount` values — they are display concerns, not ViewModel concerns.

- [ ] **Step 1: Replace the file**

```kotlin
package com.hluhovskyi.zero.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest
import java.math.BigDecimal

internal class CategoryViewProvider(
    private val viewModel: CategoryViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())

    val active = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.Active }
    }
    val inactive = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.None }
    }
    val grandTotal = remember(active) {
        active.fold(BigDecimal.ZERO) { acc, item ->
            acc + (item.spending as CategoryViewModel.Spending.Active).totalAmount.value
        }
    }

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
        item {
            Text(
                text = "Categories",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Primary,
                ),
            )
        }

        items(active, key = { it.id.value }) { category ->
            val spending = category.spending as CategoryViewModel.Spending.Active
            val barFraction = if (grandTotal > BigDecimal.ZERO) {
                (spending.totalAmount.value.toDouble() / grandTotal.toDouble())
                    .toFloat().coerceIn(0f, 1f)
            } else 0f
            val percentOfTotal = if (grandTotal > BigDecimal.ZERO) {
                (spending.totalAmount.value.toDouble() / grandTotal.toDouble() * 100).toInt()
            } else 0

            ActiveCategoryCard(
                category = category,
                spending = spending,
                formattedTotal = amountFormatter.format(spending.totalAmount),
                barFraction = barFraction,
                percentOfTotal = percentOfTotal,
                barColor = category.colorScheme.toUi().primary,
                onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                imageLoader = imageLoader,
            )
        }

        if (inactive.isNotEmpty()) {
            item {
                Text(
                    text = "Unused this month",
                    modifier = Modifier.padding(
                        start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp,
                    ),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Outline,
                        letterSpacing = 0.7.sp,
                    ),
                )
            }
            items(inactive, key = { it.id.value }) { category ->
                InactiveCategoryCard(
                    category = category,
                    onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun ActiveCategoryCard(
    category: CategoryViewModel.CategoryItem,
    spending: CategoryViewModel.Spending.Active,
    formattedTotal: String,
    barFraction: Float,
    percentOfTotal: Int,
    barColor: Color,
    onClick: () -> Unit,
    imageLoader: ImageLoader,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconView(colorScheme = category.colorScheme.toUi(), size = 40.dp) { tint ->
                imageLoader.View(
                    image = category.icon,
                    modifier = Modifier
                        .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                        .aspectRatio(1f),
                    scale = ImageLoader.Scale.Crop,
                    tint = tint,
                )
            }
            Text(
                text = category.name,
                modifier = Modifier
                    .padding(start = 14.dp)
                    .weight(1f),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                ),
            )
            Text(
                text = formattedTotal,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Primary,
                    letterSpacing = (-0.1).sp,
                ),
            )
        }
        Row(
            modifier = Modifier.padding(start = 54.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${spending.transactionCount} transaction${if (spending.transactionCount != 1) "s" else ""}",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant),
            )
            Text(
                text = "$percentOfTotal% of total",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant,
                ),
            )
        }
        SpendingBar(
            fraction = barFraction,
            color = barColor,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun InactiveCategoryCard(
    category: CategoryViewModel.CategoryItem,
    onClick: () -> Unit,
    imageLoader: ImageLoader,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(colorScheme = category.colorScheme.toUi(), size = 40.dp) { tint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier
                    .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                    .aspectRatio(1f),
                scale = ImageLoader.Scale.Crop,
                tint = tint,
            )
        }
        Text(
            text = category.name,
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            ),
        )
        Text(
            text = "No activity",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Outline,
            ),
        )
    }
}

@Composable
private fun SpendingBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(SurfaceContainer, shape = RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(color.copy(alpha = 0.75f), shape = RoundedCornerShape(2.dp)),
        )
    }
}
```

- [ ] **Step 2: Build full project**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :app:compileDebugKotlin --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and visually verify**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :app:installDebug --quiet
```

Open the app → tap **Categories** in the bottom nav. Verify:
- "Categories" heading (navy, extra bold) at top
- Categories with activity this month: icon + name + formatted total + `N transactions` + `X% of total` + coloured spending bar proportional to share
- Categories without activity: icon + name + "No activity"
- "Unused this month" label between the two groups
- Active sorted by spend descending; inactive alphabetically

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt
git commit -m "feat(categories): complete list redesign with spending stats and section divider"
```

---

## Self-review

**Spec coverage:**
- Aggregated DB query — no full table scan ✓ (Tasks 1–2: `CategorySpendingBetween` SQL `GROUP BY`)
- Generic `Period` API ✓ (Task 3: `CurrentMonth`, `CurrentWeek`, `CurrentYear`, `Between`)
- Current-month default ✓ (Task 6: `Period.CurrentMonth`)
- `Spending` sealed class `Active` / `None` ✓ (Task 5)
- `barFraction` / `percentOfTotal` in UI only ✓ (Task 8: computed from `BigDecimal` values in `CategoryView`)
- White cards 16dp radius ✓ (Task 8)
- "Categories" heading ✓ (Task 8)
- Amount, transaction count, % of total, spending bar ✓ (Task 8)
- "No activity" for zero-spend ✓ (Task 8 `InactiveCategoryCard`)
- "Unused this month" divider ✓ (Task 8)
- Sort: active by spend desc, inactive alphabetically ✓ (Task 6)
- Out-of-scope excluded: no SegmentedToggle, no summary bar, no long-press, no detail screen ✓

**Placeholder scan:** No TBDs. All code blocks are complete.

**Type consistency:**
- `TransactionRepository.Criteria.CategorySpendingBetween` uses `LocalDate` ✓ — matched in `DefaultCategorySpendingUseCase.resolve()` return type `Pair<LocalDate, LocalDate>` ✓
- `TransactionRepository.CategorySpendingStatistic.totalAmount: Amount` — passed to `currencyConvertUseCase.convertToPrimary(row.totalAmount, row.currencyId)` ✓
- `CategorySpendingUseCase.CategorySpending.totalAmount: Amount` — stored in `CategoryViewModel.Spending.Active.totalAmount: Amount` ✓
- `CategoryViewModel.Spending.Active.totalAmount.value: BigDecimal` — used in `grandTotal` fold and `barFraction` / `percentOfTotal` math ✓
- `CategoryViewProvider` constructor `amountFormatter: AmountFormatter` — matched in `CategoryComponent.Module.viewProvider` ✓
