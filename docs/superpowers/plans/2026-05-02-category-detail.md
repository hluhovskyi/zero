# Category Detail Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Category Detail screen that shows current-month spending stats and a filtered transaction list, opened by tapping a category card.

**Architecture:** `CategoryDetailComponent` owns a `CategoryDetailViewModel` (hero stats) and a pre-configured `TransactionComponent` (filtered by category, no search bar). `TransactionComponent` is parameterised with a new `TransactionFilter` sealed class and `DisplayConfig`, making it reusable for future Account Detail. `CategoryDetailViewProvider` renders the top bar, hero card, and transaction list via `Box(Modifier.weight(1f))` to avoid nested-scroll conflicts.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Room, kotlinx-datetime, JUnit 4, Mockito

**Spec:** `docs/superpowers/specs/2026-05-02-category-detail-design.md`

---

## File Map

| File | Module | Action |
|------|--------|--------|
| `zero-api/.../transactions/TransactionRepository.kt` | zero-api | Modify — add `ForCategory` criterion |
| `zero-database/.../transactions/TransactionRoom.kt` | zero-database | Modify — add `selectByCategory` DAO |
| `zero-database/.../transactions/RoomTransactionRepository.kt` | zero-database | Modify — add `ForCategory` branch |
| `zero-core/.../transactions/TransactionFilter.kt` | zero-core | Create — sealed interface |
| `zero-core/.../transactions/DisplayConfig.kt` | zero-core | Create — data class |
| `zero-core/.../transactions/TransactionComponent.kt` | zero-core | Modify — add two `@BindsInstance` builder params |
| `zero-core/.../transactions/DefaultTransactionViewModel.kt` | zero-core | Modify — add `filter` param + `ForCategory` branch |
| `zero-core/.../transactions/TransactionViewProvider.kt` | zero-core | Modify — conditional `SearchBar` via `displayConfig` |
| `zero-core/.../categories/detail/CategoryDetailViewModel.kt` | zero-core | Create — interface |
| `zero-core/.../categories/detail/DefaultCategoryDetailViewModel.kt` | zero-core | Create — implementation |
| `zero-core/.../categories/detail/OnCategoryDetailEditHandler.kt` | zero-core | Create — fun interface |
| `zero-core/.../categories/detail/OnCategoryDetailBackHandler.kt` | zero-core | Create — fun interface |
| `zero-core/.../categories/detail/CategoryDetailComponent.kt` | zero-core | Create — Dagger component |
| `zero-core/.../categories/detail/CategoryDetailViewProvider.kt` | zero-core | Create — composable |
| `app/.../navigation/Destinations.kt` | app | Modify — add `Category.Item.Detail` |
| `app/.../activity/ActivityComponent.kt` | app | Modify — add `CategoryDetailComponent.Dependencies` + builder |
| `app/.../screens/MainActivityScreenComponent.kt` | app | Modify — add dependency + two navigation entries |
| `app/.../screens/CategoryDetailScreen.kt` | app | Create — thin screen wrapper |

All paths under `src/main/java/com/hluhovskyi/zero/` (or `src/test/` for tests).

---

### Task 1: Add `ForCategory` criterion, DAO query, and repository branch

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`
- Test: `zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt`

- [ ] **Step 1: Add the failing test**

Open `RoomTransactionRepositoryPaginationTest.kt` and append these two tests (before the closing brace of the class). The helper `expenseEntity` already exists in the file:

```kotlin
@Test
fun `ForCategory criterion calls selectByCategory with the right userId and categoryId`() = runTest {
    val categoryId = Id.Known("cat1")
    whenever(transactionRoom.selectByCategory(userId.value, categoryId.value))
        .thenReturn(flowOf(emptyList()))

    repo.query(TransactionRepository.Criteria.ForCategory(categoryId)).first()

    verify(transactionRoom).selectByCategory(userId.value, categoryId.value)
}

@Test
fun `ForCategory criterion maps expense entity to Expense transaction`() = runTest {
    val categoryId = Id.Known("cat1")
    val entity = TransactionEntity(
        id = Id.Known("t1"),
        userId = userId,
        type = TransactionEntity.Type.EXPENSE,
        currencyId = Id.Known("usd"),
        accountId = Id.Known("acc"),
        categoryId = categoryId.value,
        amount = AmountEntity(java.math.BigDecimal("42.00")),
        rate = RateEntity(java.math.BigDecimal.ONE),
        targetAccount = null,
        targetAmount = AmountEntity(java.math.BigDecimal.ZERO),
        enteredDateTime = jan15h10,
        creationDateTime = jan15h10,
        updatedDateTime = jan15h10,
    )
    whenever(transactionRoom.selectByCategory(userId.value, categoryId.value))
        .thenReturn(flowOf(listOf(entity)))

    val result = repo.query(TransactionRepository.Criteria.ForCategory(categoryId)).first()

    assertEquals(1, result.size)
    val tx = result.first()
    assertTrue(tx is TransactionRepository.Transaction.Expense)
    assertEquals(Id.Known("t1"), tx.id)
}
```

- [ ] **Step 2: Run the tests — expect compilation failure** (`ForCategory` does not exist yet)

```bash
./gradlew :zero-database:test --tests "*.RoomTransactionRepositoryPaginationTest" 2>&1 | tail -20
```

Expected: compile error mentioning `ForCategory`.

- [ ] **Step 3: Add `ForCategory` to `TransactionRepository.Criteria`**

In `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`, add one line inside `sealed interface Criteria<T>`:

```kotlin
data class ForCategory(val categoryId: Id.Known) : Criteria<List<Transaction>>
```

The full `Criteria` block after the change:

```kotlin
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
    data class ForCategory(val categoryId: Id.Known) : Criteria<List<Transaction>>
}
```

- [ ] **Step 4: Add `selectByCategory` DAO query to `TransactionRoom`**

In `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`, add after the existing `search` query:

```kotlin
@Query(
    """
    SELECT * FROM TransactionEntity
    WHERE userId     = :userId
      AND categoryId = :categoryId
      AND deletedAt  IS NULL
    ORDER BY datetime(enteredDateTime) DESC
""",
)
fun selectByCategory(userId: String, categoryId: String): Flow<List<TransactionEntity>>
```

- [ ] **Step 5: Add `ForCategory` branch to `RoomTransactionRepository`**

In `RoomTransactionRepository.kt`, inside the `when (criteria)` block in `query()`, add after the `CategorySpendingBetween` branch:

```kotlin
is TransactionRepository.Criteria.ForCategory -> transactionRoom()
    .selectByCategory(userId.value, criteria.categoryId.value)
    .map { entities -> entities.mapNotNull { it.toRepository() } }
```

- [ ] **Step 6: Run the tests — expect PASS**

```bash
./gradlew :zero-database:test --tests "*.RoomTransactionRepositoryPaginationTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
git add zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt
git commit -m "feat: add ForCategory transaction query criterion"
```

---

### Task 2: `TransactionFilter`, `DisplayConfig`, and `TransactionComponent` builder params

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DisplayConfig.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt`

No behaviour changes in this task — only type and wiring scaffolding. Existing tests must still pass.

- [ ] **Step 1: Create `TransactionFilter`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

sealed interface TransactionFilter {
    object All : TransactionFilter
    data class ForCategory(val categoryId: Id.Known) : TransactionFilter
}
```

- [ ] **Step 2: Create `DisplayConfig`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DisplayConfig.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

data class DisplayConfig(val showSearchBar: Boolean = true)
```

- [ ] **Step 3: Add `@BindsInstance` methods to `TransactionComponent.Builder` and set defaults in `companion.builder()`**

Replace the entire `TransactionComponent.kt` file content:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionScope

private const val TAG = "TransactionComponent"

@TransactionScope
@dagger.Component(
    modules = [TransactionComponent.Module::class],
    dependencies = [TransactionComponent.Dependencies::class],
)
abstract class TransactionComponent : AttachableViewComponent {

    internal abstract val viewModel: TransactionViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
        val clock: Clock
        val zoneProvider: ZoneProvider

        val transactionRepository: TransactionRepository
        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val iconRepository: IconRepository
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionComponent.builder()
            .dependencies(dependencies)
            .onTransactionSelectHandler(OnTransactionSelectedHandler.Noop)
            .transactionFilter(TransactionFilter.All)
            .displayConfig(DisplayConfig())
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onTransactionSelectHandler(handler: OnTransactionSelectedHandler): Builder

        @BindsInstance
        fun transactionFilter(filter: TransactionFilter): Builder

        @BindsInstance
        fun displayConfig(config: DisplayConfig): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionScope
        fun viewModel(
            transactionRepository: TransactionRepository,
            accountRepository: AccountRepository,
            currencyRepository: CurrencyRepository,
            iconRepository: IconRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            currencyConvertUseCase: CurrencyConvertUseCase,
            onTransactionSelectedHandler: OnTransactionSelectedHandler,
            filter: TransactionFilter,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): TransactionViewModel = DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
            onTransactionSelectedHandler = onTransactionSelectedHandler,
            filter = filter,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @TransactionScope
        fun viewProvider(
            viewModel: TransactionViewModel,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
            dateFormatter: DateFormatter,
            displayConfig: DisplayConfig,
        ): ViewProvider = TransactionViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
        )
    }
}
```

- [ ] **Step 4: Verify build compiles** (implementation stubs will fail later — expect compile errors in `DefaultTransactionViewModel` and `TransactionViewProvider` only)

```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: errors about `DefaultTransactionViewModel` missing `filter` param and `TransactionViewProvider` missing `displayConfig` param. No other errors.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionFilter.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DisplayConfig.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt
git commit -m "feat: add TransactionFilter and DisplayConfig to TransactionComponent"
```

---

### Task 3: Parameterise `DefaultTransactionViewModel` with `TransactionFilter`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt`

- [ ] **Step 1: Add failing tests**

Append these tests to `DefaultTransactionViewModelTest.kt` (before the closing brace). Also add `createViewModel` overload that accepts `filter`:

```kotlin
@Test
fun `ForCategory filter queries ForCategory criterion instead of All`() = runTest {
    val categoryId = Id.Known("cat1")
    val viewModel = createViewModel(backgroundScope, filter = TransactionFilter.ForCategory(categoryId))
    viewModel.attach()
    runCurrent()

    val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
    verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())

    val forCat = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.ForCategory>()
    assertEquals(1, forCat.size)
    assertEquals(categoryId, forCat.first().categoryId)

    val allCriteria = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.All>()
    assertEquals(0, allCriteria.size)
}

@Test
fun `ForCategory filter makes LoadMore a no-op`() = runTest {
    val categoryId = Id.Known("cat1")
    val viewModel = createViewModel(backgroundScope, filter = TransactionFilter.ForCategory(categoryId))
    viewModel.attach()
    runCurrent()

    viewModel.perform(TransactionViewModel.Action.LoadMore)
    advanceUntilIdle()

    // No Criteria.All should have been queried at any point
    val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
    verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())
    val allCriteria = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.All>()
    assertEquals(0, allCriteria.size)
}
```

Also update the existing `createViewModel` helper and add the overload:

```kotlin
private fun createViewModel(
    coroutineScope: CoroutineScope,
    filter: TransactionFilter = TransactionFilter.All,
) = DefaultTransactionViewModel(
    transactionRepository = transactionRepository,
    accountRepository = accountRepository,
    currencyRepository = currencyRepository,
    iconRepository = iconRepository,
    categoriesQueryUseCase = categoriesQueryUseCase,
    currencyPrimaryUseCase = currencyPrimaryUseCase,
    currencyConvertUseCase = currencyConvertUseCase,
    onTransactionSelectedHandler = onTransactionSelectedHandler,
    filter = filter,
    clock = fakeClock,
    zoneProvider = fakeZoneProvider,
    coroutineScope = coroutineScope,
)
```

(Replace the existing `createViewModel` with this version — it adds `filter` with default `TransactionFilter.All`, so existing test call-sites still compile.)

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./gradlew :zero-core:test --tests "*.DefaultTransactionViewModelTest" 2>&1 | tail -20
```

Expected: compile error — `DefaultTransactionViewModel` has no `filter` parameter yet.

- [ ] **Step 3: Update `DefaultTransactionViewModel`**

Add `filter: TransactionFilter = TransactionFilter.All` constructor parameter (after `onTransactionSelectedHandler`) and replace the `pagedTransactions` derivation in `attach()` with a branch:

Full updated `DefaultTransactionViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val iconRepository: IconRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val onTransactionSelectedHandler: OnTransactionSelectedHandler,
    private val filter: TransactionFilter = TransactionFilter.All,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : TransactionViewModel {

    private val mutableState = MutableStateFlow(TransactionViewModel.State())
    override val state: Flow<TransactionViewModel.State> = mutableState

    private val loadMoreTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: TransactionViewModel.Action) {
        when (action) {
            is TransactionViewModel.Action.SelectTransaction -> {
                onTransactionSelectedHandler.onSelected(action.item.id)
            }

            is TransactionViewModel.Action.LoadMore -> {
                if (filter is TransactionFilter.All && mutableState.value.searchQuery.isBlank()) {
                    coroutineScope.launch {
                        loadMoreTrigger.emit(Unit)
                    }
                }
            }

            is TransactionViewModel.Action.UpdateSearchQuery -> {
                mutableState.update { it.copy(searchQuery = action.query) }
            }

            is TransactionViewModel.Action.DeleteTransaction -> {
                coroutineScope.launch {
                    transactionRepository.delete(action.id)
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val initialTimestamp = clock.localDateTime(zoneProvider.timeZone())

            val pagedTransactions: Flow<List<TransactionRepository.Transaction>> = when (filter) {
                TransactionFilter.All -> {
                    combine(
                        transactionRepository.query(TransactionRepository.Criteria.After(initialTimestamp))
                            .onStartWithEmptyList()
                            .onEmptyReturnEmptyList(),
                        transactionRepository.query(
                            TransactionRepository.Criteria.All(),
                            trigger = loadMoreTrigger,
                        )
                            .onStartWithEmptyList()
                            .onEmptyReturnEmptyList(),
                    ) { new, paged ->
                        val freshById = new.associateBy { it.id }
                        val merged = paged.map { transaction ->
                            val fresh = freshById[transaction.id]
                            if (fresh != null && fresh.updatedDateTime >= transaction.updatedDateTime) {
                                fresh
                            } else {
                                transaction
                            }
                        }
                        val existingIds = paged.map { it.id }.toSet()
                        val added = new.filter { it.id !in existingIds }
                        (added + merged).sortedByDescending { it.dateTime }
                    }
                }
                is TransactionFilter.ForCategory -> {
                    transactionRepository.query(TransactionRepository.Criteria.ForCategory(filter.categoryId))
                        .onStartWithEmptyList()
                        .onEmptyReturnEmptyList()
                }
            }

            val searchTransactions = mutableState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(null)
                    } else {
                        flow<List<TransactionRepository.Transaction>?> {
                            delay(300L)
                            emitAll(transactionRepository.query(TransactionRepository.Criteria.Search(query)))
                        }
                    }
                }

            val activeTransactions = combine(pagedTransactions, searchTransactions) { paged, searchResult ->
                searchResult ?: paged
            }

            combine(
                activeTransactions,
                categoriesQueryUseCase.queryAll()
                    .onStartWithEmptyList()
                    .onEmptyReturnEmptyList()
                    .associateById(),
                accountRepository.query(AccountRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
                currencyRepository.query(CurrencyRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
                iconRepository.query(IconRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
            ) { transactions, idToCategories, idToAccounts, idToCurrencies, idToIcons ->
                val primaryCurrency = currencyPrimaryUseCase.getPrimaryCurrency()
                transactions
                    .mapNotNull { transaction ->
                        resolve(
                            transaction = transaction,
                            idToAccounts = idToAccounts,
                            idToCategories = idToCategories,
                            idToCurrencies = idToCurrencies,
                            idToIcons = idToIcons,
                        )
                    }
                    .groupBy { it.date.date }
                    .flatMap { (date, transactions) ->
                        val amount: Amount = transactions.fold(Amount.zero()) { amount, transaction ->
                            when (transaction) {
                                is TransactionViewModel.Item.Transaction.Expense -> {
                                    amount - if (transaction.conversion is TransactionViewModel.Conversion.WithAmount &&
                                        transaction.conversion.currencyId == primaryCurrency.id
                                    ) {
                                        transaction.conversion.amount
                                    } else {
                                        currencyConvertUseCase.convertToPrimary(
                                            transaction.amount,
                                            transaction.currencyId,
                                        )
                                    }
                                }
                                is TransactionViewModel.Item.Transaction.Income -> {
                                    amount + if (transaction.conversion is TransactionViewModel.Conversion.WithAmount &&
                                        transaction.conversion.currencyId == primaryCurrency.id
                                    ) {
                                        transaction.conversion.amount
                                    } else {
                                        currencyConvertUseCase.convertToPrimary(
                                            transaction.amount,
                                            transaction.currencyId,
                                        )
                                    }
                                }
                                is TransactionViewModel.Item.Transaction.Transfer -> amount - currencyConvertUseCase.convertToPrimary(
                                    transaction.amount,
                                    transaction.currencyId,
                                ) + currencyConvertUseCase.convertToPrimary(
                                    transaction.targetAmount,
                                    transaction.targetCurrencyId,
                                )
                            }
                        }

                        listOf(
                            TransactionViewModel.Item.Summary(
                                date = date,
                                total = amount,
                                currencySymbol = primaryCurrency.symbol,
                            ),
                        ) + transactions
                    }
            }.collectLatest { items ->
                mutableState.update { state ->
                    state.copy(transactions = items)
                }
            }
        }
    }

    private fun resolve(
        transaction: TransactionRepository.Transaction,
        idToCategories: Map<Id.Known, CategoriesQueryUseCase.Category>,
        idToAccounts: Map<Id.Known, AccountRepository.Account>,
        idToCurrencies: Map<Id.Known, Currency>,
        idToIcons: Map<Id.Known, Icon>,
    ): TransactionViewModel.Item.Transaction? {
        return when (transaction) {
            is TransactionRepository.Transaction.Expense -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Expense(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    currencyId = transaction.currencyId,
                    conversion = if (transaction.currencyId != account.currencyId) {
                        val symbol = idToCurrencies[account.currencyId]?.symbol
                        TransactionViewModel.Conversion.WithAmount(
                            amount = transaction.amount.withRate(transaction.rate),
                            currencyId = account.currencyId,
                            currencySymbol = symbol.orEmpty(),
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                    currencySymbol = currency.symbol,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    categoryName = category.name,
                    categoryColorScheme = category.colorScheme,
                    categoryIcon = category.icon,
                )
            }

            is TransactionRepository.Transaction.Income -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Income(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    currencySymbol = currency.symbol,
                    currencyId = transaction.currencyId,
                    categoryName = category.name,
                    categoryColorScheme = category.colorScheme,
                    categoryIcon = category.icon,
                    conversion = if (transaction.currencyId != account.currencyId) {
                        val symbol = idToCurrencies[account.currencyId]?.symbol
                        TransactionViewModel.Conversion.WithAmount(
                            amount = transaction.amount.withRate(transaction.rate),
                            currencyId = account.currencyId,
                            currencySymbol = symbol.orEmpty(),
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                )
            }

            is TransactionRepository.Transaction.Transfer -> {
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null
                val targetAccount = idToAccounts[transaction.targetAccount] ?: return null
                val targetCurrency = idToCurrencies[targetAccount.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Transfer(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    accountName = account.name,
                    currencyId = transaction.currencyId,
                    currencySymbol = currency.symbol,
                    targetAccountName = targetAccount.name,
                    targetAmount = transaction.targetAmount,
                    targetCurrencyId = targetCurrency.id,
                    targetCurrencySymbol = targetCurrency.symbol,
                    transferIcon = idToIcons[IconRepository.transferIconId()]?.image ?: Image.empty(),
                    transferColorScheme = ColorScheme.Grey,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run all zero-core tests — expect PASS**

```bash
./gradlew :zero-core:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt
git commit -m "feat: add ForCategory filter branch to DefaultTransactionViewModel"
```

---

### Task 4: Conditional `SearchBar` in `TransactionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

No new tests — UI rendering is verified by UI inspector after full integration.

- [ ] **Step 1: Update `TransactionViewProvider` to accept `displayConfig`**

Replace the constructor and pass `displayConfig` into `TransactionView`. The only change in rendering is the `SearchBar` is guarded by `displayConfig.showSearchBar`. Full updated file:

```kotlin
package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transaction.TransactionExpenseView
import com.hluhovskyi.zero.transaction.TransactionIncomeView
import com.hluhovskyi.zero.transaction.TransactionTransferView
import com.hluhovskyi.zero.ui.SearchBar
import com.hluhovskyi.zero.ui.common.toUi

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
    private val displayConfig: DisplayConfig = DisplayConfig(),
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
    displayConfig: DisplayConfig,
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())
    var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
    val lazyListState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleIndex >= totalItems - 30
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.perform(TransactionViewModel.Action.LoadMore)
        }
    }

    Column(modifier = Modifier.fillMaxSize().focusTarget()) {
        if (displayConfig.showSearchBar) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery(it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.searchQuery.isNotBlank() && state.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No transactions found",
                    fontSize = 15.sp,
                    color = Color(0xFF44464F),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                items(state.transactions) { transaction ->
                    when (transaction) {
                        is TransactionViewModel.Item.Summary -> {
                            val isFirst = state.transactions.first() == transaction
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (isFirst) 8.dp else 20.dp,
                                        bottom = 8.dp,
                                        start = 4.dp,
                                        end = 4.dp,
                                    ),
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = dateFormatter.format(transaction).uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF44464F),
                                    letterSpacing = 0.8.sp,
                                )
                                Text(
                                    text = amountFormatter.format(
                                        amount = transaction.total,
                                        currencySymbol = transaction.currencySymbol,
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF44464F),
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                        is TransactionViewModel.Item.Transaction -> {
                            val cardShape = RoundedCornerShape(12.dp)
                            val contentModifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction)) },
                                    onLongClick = { expandedItemId = transaction.id },
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp)
                                    .clip(cardShape)
                                    .background(Color(0xFFFFFFFF)),
                            ) {
                                when (transaction) {
                                    is TransactionViewModel.Item.Transaction.Expense ->
                                        TransactionExpenseView(
                                            modifier = contentModifier,
                                            categoryName = transaction.categoryName,
                                            amount = amountFormatter.format(
                                                amount = transaction.amount,
                                                currencySymbol = transaction.currencySymbol,
                                            ),
                                            accountName = transaction.accountName,
                                            iconColorScheme = transaction.categoryColorScheme.toUi(),
                                            accountIcon = transaction.accountIcon.toComposable(
                                                imageLoader = imageLoader,
                                                modifier = Modifier
                                                    .alpha(ContentAlpha.medium)
                                                    .padding(end = 6.dp)
                                                    .size(20.dp),
                                            ),
                                            convertedAmount = transaction.conversion.format(amountFormatter),
                                            icon = transaction.categoryIcon.toTintedComposable(
                                                imageLoader = imageLoader,
                                                modifier = Modifier.size(24.dp),
                                            ),
                                        )
                                    is TransactionViewModel.Item.Transaction.Income -> {
                                        TransactionIncomeView(
                                            modifier = contentModifier,
                                            categoryName = transaction.categoryName,
                                            amount = amountFormatter.format(
                                                amount = transaction.amount,
                                                currencySymbol = transaction.currencySymbol,
                                            ),
                                            accountName = transaction.accountName,
                                            iconColorScheme = transaction.categoryColorScheme.toUi(),
                                            convertedAmount = transaction.conversion.format(amountFormatter),
                                            icon = transaction.categoryIcon.toTintedComposable(
                                                imageLoader = imageLoader,
                                                modifier = Modifier.size(24.dp),
                                            ),
                                        )
                                    }
                                    is TransactionViewModel.Item.Transaction.Transfer -> {
                                        TransactionTransferView(
                                            modifier = contentModifier,
                                            sourceAccountName = transaction.accountName,
                                            targetAccountName = transaction.targetAccountName,
                                            sourceAmount = amountFormatter.format(
                                                amount = transaction.amount,
                                                currencySymbol = transaction.currencySymbol,
                                            ),
                                            targetAmount = amountFormatter.format(
                                                amount = transaction.targetAmount,
                                                currencySymbol = transaction.targetCurrencySymbol,
                                            ),
                                            transferIconColorScheme = transaction.transferColorScheme.toUi(),
                                            transferIcon = transaction.transferIcon.toTintedComposable(
                                                imageLoader = imageLoader,
                                                modifier = Modifier.size(24.dp),
                                            ),
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedItemId == transaction.id,
                                    onDismissRequest = { expandedItemId = null },
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            viewModel.perform(TransactionViewModel.Action.DeleteTransaction(transaction.id))
                                            expandedItemId = null
                                        },
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFBA1A1A),
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Text(
                                                text = "Delete",
                                                color = Color(0xFFBA1A1A),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TransactionViewModel.Conversion.format(
    amountFormatter: AmountFormatter,
): String? = if (this is TransactionViewModel.Conversion.WithAmount) {
    amountFormatter.format(
        amount = amount,
        currencySymbol = currencySymbol,
    )
} else {
    null
}

private fun DateFormatter.format(
    transaction: TransactionViewModel.Item.Summary,
): String = format(
    date = transaction.date,
    dayConfig = DateFormatter.DayConfig.WithoutZero,
    monthConfig = DateFormatter.MonthConfig.Readable,
    yearConfig = DateFormatter.YearConfig.SkipCurrent,
)

private fun Image.toComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
): @Composable () -> Unit = {
    imageLoader.View(
        image = this,
        modifier = modifier,
    )
}

private fun Image.toTintedComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
): @Composable (tint: Color) -> Unit = { tint ->
    imageLoader.View(
        image = this,
        modifier = modifier,
        tint = tint,
    )
}
```

- [ ] **Step 2: Run all zero-core tests — expect PASS**

```bash
./gradlew :zero-core:test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: conditional SearchBar in TransactionViewProvider via DisplayConfig"
```

---

### Task 5: `CategoryDetailViewModel` interface and `DefaultCategoryDetailViewModel`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewModel.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModel.kt`
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModelTest.kt`

- [ ] **Step 1: Create the interface**

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CategoryDetailViewModel : AttachableActionStateModel<CategoryDetailViewModel.Action, CategoryDetailViewModel.State> {

    sealed interface Action {
        object Edit : Action
        object Back : Action
    }

    data class State(
        val categoryName: String = "",
        val categoryIcon: Image = Image.empty(),
        val categoryColorScheme: ColorScheme = ColorScheme.Grey,
        val periodLabel: String = "",
        val totalAmount: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val transactionCount: Int = 0,
        val averageAmount: Amount = Amount.zero(),
        val largestAmount: Amount = Amount.zero(),
    )

    object Noop : CategoryDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): java.io.Closeable = java.io.Closeable { }
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModelTest.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultCategoryDetailViewModelTest {

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase
    @Mock private lateinit var categorySpendingUseCase: CategorySpendingUseCase
    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var onEditHandler: OnCategoryDetailEditHandler
    @Mock private lateinit var onBackHandler: OnCategoryDetailBackHandler

    private val categoryId = Id.Known("cat1")
    private val primaryCurrency = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
    private val fixedInstant = Instant.parse("2026-05-02T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeClock = object : Clock { override fun now() = fixedInstant }
    private val fakeZoneProvider = object : ZoneProvider { override fun timeZone() = testTimeZone }

    @Before
    fun setUp() {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(emptyFlow())
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForCategory>(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)
    }

    @Test
    fun `state reflects category name and color scheme from queryById`() = runTest {
        val blueScheme = ColorScheme(
            primary = Color(id = Id("p"), value = ColorValue(0xFF1565C0UL)),
            background = Color(id = Id("b"), value = ColorValue(0xFFE3F2FDUL)),
        )
        val category = CategoriesQueryUseCase.Category(
            id = categoryId,
            name = "Groceries",
            icon = Image.empty(),
            colorScheme = blueScheme,
        )
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        val state = vm.state.first()
        assertEquals("Groceries", state.categoryName)
        assertEquals(blueScheme, state.categoryColorScheme)
    }

    @Test
    fun `state total and transactionCount come from CategorySpendingUseCase for this category`() = runTest {
        val spending = CategorySpendingUseCase.CategorySpending(
            categoryId = categoryId,
            totalAmount = Amount(BigDecimal("150.00")),
            transactionCount = 5,
        )
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(listOf(spending)))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        val state = vm.state.first()
        assertEquals(Amount(BigDecimal("150.00")), state.totalAmount)
        assertEquals(5, state.transactionCount)
    }

    @Test
    fun `state averageAmount is totalAmount divided by transactionCount`() = runTest {
        val spending = CategorySpendingUseCase.CategorySpending(
            categoryId = categoryId,
            totalAmount = Amount(BigDecimal("100.00")),
            transactionCount = 4,
        )
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(listOf(spending)))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        val state = vm.state.first()
        // 100 / 4 = 25.00
        assertEquals(0, BigDecimal("25.00").compareTo(state.averageAmount.value))
    }

    @Test
    fun `state averageAmount is zero when transactionCount is zero`() = runTest {
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        assertEquals(Amount.zero(), vm.state.first().averageAmount)
    }

    @Test
    fun `state currencySymbol matches primary currency`() = runTest {
        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        assertEquals("$", vm.state.first().currencySymbol)
    }

    @Test
    fun `periodLabel is formatted as month name and year`() = runTest {
        val vm = createViewModel(backgroundScope)
        vm.attach()
        advanceUntilIdle()

        // fixedInstant = 2026-05-02 → "May 2026"
        assertEquals("May 2026", vm.state.first().periodLabel)
    }

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultCategoryDetailViewModel(
        categoryId = categoryId,
        categoriesQueryUseCase = categoriesQueryUseCase,
        categorySpendingUseCase = categorySpendingUseCase,
        transactionRepository = transactionRepository,
        currencyConvertUseCase = currencyConvertUseCase,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        onEditHandler = onEditHandler,
        onBackHandler = onBackHandler,
        clock = fakeClock,
        zoneProvider = fakeZoneProvider,
        coroutineScope = coroutineScope,
    )
}
```

- [ ] **Step 3: Run tests — expect compilation failure** (`DefaultCategoryDetailViewModel` missing)

```bash
./gradlew :zero-core:test --tests "*.DefaultCategoryDetailViewModelTest" 2>&1 | tail -10
```

Expected: compile error.

- [ ] **Step 4: Create handler interfaces**

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/OnCategoryDetailEditHandler.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailEditHandler {
    fun onEdit()

    object Noop : OnCategoryDetailEditHandler {
        override fun onEdit() = Unit
    }
}
```

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/OnCategoryDetailBackHandler.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailBackHandler {
    fun onBack()

    object Noop : OnCategoryDetailBackHandler {
        override fun onBack() = Unit
    }
}
```

- [ ] **Step 5: Create `DefaultCategoryDetailViewModel`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import java.io.Closeable
import java.math.BigDecimal
import java.math.RoundingMode

internal class DefaultCategoryDetailViewModel(
    private val categoryId: Id.Known,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onEditHandler: OnCategoryDetailEditHandler,
    private val onBackHandler: OnCategoryDetailBackHandler,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : CategoryDetailViewModel {

    private val mutableState = MutableStateFlow(CategoryDetailViewModel.State())
    override val state: Flow<CategoryDetailViewModel.State> = mutableState

    override fun perform(action: CategoryDetailViewModel.Action) {
        when (action) {
            CategoryDetailViewModel.Action.Edit -> coroutineScope.launch(Dispatchers.Main) {
                onEditHandler.onEdit()
            }
            CategoryDetailViewModel.Action.Back -> coroutineScope.launch(Dispatchers.Main) {
                onBackHandler.onBack()
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val primaryCurrency = currencyPrimaryUseCase.getPrimaryCurrency()
            val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
            val monthStart = LocalDate(today.year, today.month, 1)
            val periodLabel = today.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() } + " " + today.year

            combine(
                categoriesQueryUseCase.queryById(categoryId),
                categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth),
                transactionRepository.query(TransactionRepository.Criteria.ForCategory(categoryId)),
            ) { category, spendingList, transactions ->
                val spending = spendingList.firstOrNull { it.categoryId == categoryId }
                val total = spending?.totalAmount ?: Amount.zero()
                val count = spending?.transactionCount ?: 0
                val average = if (count > 0) {
                    Amount(total.value.divide(BigDecimal(count), 2, RoundingMode.HALF_UP))
                } else {
                    Amount.zero()
                }

                val largest = transactions
                    .filter { tx -> tx.dateTime.date >= monthStart && tx.dateTime.date <= today }
                    .filterNot { it is TransactionRepository.Transaction.Transfer }
                    .fold(Amount.zero()) { max, tx ->
                        val converted = currencyConvertUseCase.convertToPrimary(tx.amount, tx.currencyId)
                        if (converted > max) converted else max
                    }

                CategoryDetailViewModel.State(
                    categoryName = category.name,
                    categoryIcon = category.icon,
                    categoryColorScheme = category.colorScheme,
                    periodLabel = periodLabel,
                    totalAmount = total,
                    currencySymbol = primaryCurrency.symbol,
                    transactionCount = count,
                    averageAmount = average,
                    largestAmount = largest,
                )
            }.collectLatest { newState ->
                mutableState.update { newState }
            }
        }
    }
}
```

- [ ] **Step 6: Run tests — expect PASS**

```bash
./gradlew :zero-core:test --tests "*.DefaultCategoryDetailViewModelTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/
git add zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/
git commit -m "feat: add CategoryDetailViewModel and DefaultCategoryDetailViewModel"
```

---

### Task 6: `CategoryDetailComponent` and `CategoryDetailViewProvider`

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailComponent.kt`
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt`

Build verification only — no unit tests for DI wiring or composables.

- [ ] **Step 1: Create `CategoryDetailComponent`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailComponent.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.categories.DefaultCategorySpendingUseCase
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.OnTransactionSelectedHandler
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionFilter
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryDetailScope

private const val TAG = "CategoryDetailComponent"

@CategoryDetailScope
@dagger.Component(
    modules = [CategoryDetailComponent.Module::class],
    dependencies = [CategoryDetailComponent.Dependencies::class],
)
abstract class CategoryDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryDetailViewModel

    override val tag: String = TAG

    override fun attach(): Closeable {
        val vmCloseable = viewModel.attach()
        val txCloseable = viewProvider.transactionComponent.attach()
        return Closeables.from {
            vmCloseable.close()
            txCloseable.close()
        }
    }

    // Exposed so attach() can reach the TransactionComponent lifecycle
    internal abstract val viewProvider: CategoryDetailViewProvider

    interface Dependencies {
        val transactionComponentBuilder: TransactionComponent.Builder
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val categoriesQueryUseCase: CategoriesQueryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val transactionRepository: TransactionRepository
        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerCategoryDetailComponent.builder()
            .dependencies(dependencies)
            .onEditHandler(OnCategoryDetailEditHandler.Noop)
            .onBackHandler(OnCategoryDetailBackHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun categoryId(id: Id.Known): Builder

        @BindsInstance
        fun onEditHandler(handler: OnCategoryDetailEditHandler): Builder

        @BindsInstance
        fun onBackHandler(handler: OnCategoryDetailBackHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryDetailScope
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

        @Provides
        @CategoryDetailScope
        fun viewModel(
            categoryId: Id.Known,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            categorySpendingUseCase: CategorySpendingUseCase,
            transactionRepository: TransactionRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            onEditHandler: OnCategoryDetailEditHandler,
            onBackHandler: OnCategoryDetailBackHandler,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): CategoryDetailViewModel = DefaultCategoryDetailViewModel(
            categoryId = categoryId,
            categoriesQueryUseCase = categoriesQueryUseCase,
            categorySpendingUseCase = categorySpendingUseCase,
            transactionRepository = transactionRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            onEditHandler = onEditHandler,
            onBackHandler = onBackHandler,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @CategoryDetailScope
        fun transactionComponent(
            builder: TransactionComponent.Builder,
            categoryId: Id.Known,
        ): TransactionComponent = builder
            .transactionFilter(TransactionFilter.ForCategory(categoryId))
            .displayConfig(DisplayConfig(showSearchBar = false))
            .onTransactionSelectHandler(OnTransactionSelectedHandler.Noop)
            .build()

        @Provides
        @CategoryDetailScope
        fun viewProvider(
            viewModel: CategoryDetailViewModel,
            transactionComponent: TransactionComponent,
            imageLoader: ImageLoader,
            amountFormatter: AmountFormatter,
        ): CategoryDetailViewProvider = CategoryDetailViewProvider(
            viewModel = viewModel,
            transactionViewProvider = transactionComponent.viewProvider,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
```

- [ ] **Step 2: Create `CategoryDetailViewProvider`**

Create `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt`:

```kotlin
package com.hluhovskyi.zero.categories.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

internal class CategoryDetailViewProvider(
    private val viewModel: CategoryDetailViewModel,
    private val transactionViewProvider: ViewProvider,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = CategoryDetailViewModel.State())
        val colorScheme = state.categoryColorScheme.toUi()

        Column(Modifier.fillMaxSize()) {
            TopBar(state.categoryName, viewModel)
            HeroCard(state, colorScheme, imageLoader, amountFormatter)
            Box(Modifier.weight(1f)) {
                transactionViewProvider.View()
            }
        }
    }
}

@Composable
private fun TopBar(categoryName: String, viewModel: CategoryDetailViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.perform(CategoryDetailViewModel.Action.Back) }) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PrimaryContainer,
            )
        }
        Text(
            text = categoryName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryContainer,
            ),
        )
        IconButton(onClick = { viewModel.perform(CategoryDetailViewModel.Action.Edit) }) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = PrimaryContainer,
            )
        }
    }
}

@Composable
private fun HeroCard(
    state: CategoryDetailViewModel.State,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.background)
            .fillMaxWidth(),
    ) {
        // Ghosted icon background
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.15f)
                .size(80.dp),
        ) {
            imageLoader.View(
                image = state.categoryIcon,
                modifier = Modifier.fillMaxSize(),
                tint = colorScheme.primary,
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = state.periodLabel.uppercase(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = amountFormatter.format(state.totalAmount, state.currencySymbol),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.primary,
                    letterSpacing = (-0.72).sp,
                ),
            )
            Spacer(Modifier.size(16.dp))
            Row {
                StatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "AVG PER TX",
                    value = amountFormatter.format(state.averageAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "LARGEST",
                    value = amountFormatter.format(state.largestAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, colorScheme: UiColorScheme) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary.copy(alpha = 0.7f),
                letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
            ),
        )
    }
}
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :zero-core:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/
git commit -m "feat: add CategoryDetailComponent and CategoryDetailViewProvider"
```

---

### Task 7: Navigation wiring

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`
- Create: `app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoryDetailScreen.kt`

- [ ] **Step 1: Add `Category.Item.Detail` destination**

In `Destinations.kt`, replace the `Category.Item` block:

```kotlin
sealed interface Item : Category {
    object CategoryId : Argument<Id> by idValueOf("categoryId")

    object Detail : Item, Destination by destinationOf("categories/{categoryId}", CategoryId)

    object Edit : Item, Destination by destinationOf("categories/{categoryId}/edit", CategoryId)
}
```

(Move `CategoryId` to the `Item` level so both `Detail` and `Edit` share it.)

- [ ] **Step 2: Fix the existing `categoryEditItemNavigationEntry` to use the shared `CategoryId`**

In `MainActivityScreenComponent.kt`, update the two references that previously read `Destinations.Category.Item.Edit.CategoryId`:

```kotlin
// categoryEditNavigationEntry — unchanged (no categoryId needed, uses Id.Unknown)

// categoryEditItemNavigationEntry
fun categoryEditItemNavigationEntry(...): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Item.Edit) {
    componentBuilder
        .categoryId(arguments.getValue(Destinations.Category.Item.CategoryId))  // was Edit.CategoryId
        ...
}
```

- [ ] **Step 3: Add `CategoryDetailComponent.Dependencies` to `ActivityComponent`**

In `ActivityComponent.kt`:

1. Add the import at the top:
```kotlin
import com.hluhovskyi.zero.categories.detail.CategoryDetailComponent
```

2. Add `CategoryDetailComponent.Dependencies` to the `abstract class ActivityComponent :` list:
```kotlin
abstract class ActivityComponent :
    AttachableViewComponent,
    BottomBarComponent.Dependencies,
    MainActivityScreenComponent.Dependencies,
    AccountComponent.Dependencies,
    AccountEditComponent.Dependencies,
    CategoryComponent.Dependencies,
    CategoryDetailComponent.Dependencies,          // NEW
    CategoryPickerComponent.Dependencies,
    CurrencyPickerComponent.Dependencies,
    CategoryEditComponent.Dependencies,
    TransactionComponent.Dependencies,
    TransactionEditComponent.Dependencies,
    TransactionPreviewComponent.Dependencies,
    IconPickerComponent.Dependencies,
    ColorPickerComponent.Dependencies {
```

`ActivityComponent` already satisfies every field in `CategoryDetailComponent.Dependencies`:
- `transactionComponentBuilder` — already provided by `ActivityComponent.Module.transactionComponentBuilder()` (no new provider needed)
- All other fields (`imageLoader`, `amountFormatter`, `categoriesQueryUseCase`, etc.) — already present via `ActivityComponent.Dependencies`

3. Add the builder provider in `ActivityComponent.Module`:
```kotlin
@Provides
@ActivityScope
fun categoryDetailComponentBuilder(
    component: ActivityComponent,
): CategoryDetailComponent.Builder = CategoryDetailComponent.builder(component)
```

- [ ] **Step 4: Update `MainActivityScreenComponent`**

Add `categoryDetailComponentBuilder` to `Dependencies` and add the two navigation entries:

In `MainActivityScreenComponent.Dependencies`, add:
```kotlin
val categoryDetailComponentBuilder: CategoryDetailComponent.Builder
```

Change the existing `categoryNavigationEntry` to navigate to `Detail` on tap:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun categoryNavigationEntry(
    componentBuilder: CategoryComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.composable(Destinations.Category.All) {
    CategoriesScreen(
        component = componentBuilder
            .onCategorySelectedHandler { categoryId ->
                navigator.navigateTo(
                    Destinations.Category.Item.Detail,
                    Destinations.Category.Item.CategoryId.withValue(categoryId),
                )
            }
            .logging(logger),
        onCategoriesEdit = { navigator.navigateTo(Destinations.Category.Edit) },
    )
}
```

Add the new `categoryDetailNavigationEntry`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun categoryDetailNavigationEntry(
    componentBuilder: CategoryDetailComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Item.Detail) {
    val categoryId = arguments.getValue(Destinations.Category.Item.CategoryId)
    componentBuilder
        .categoryId(categoryId)
        .onBackHandler { navigator.back() }
        .onEditHandler {
            navigator.navigateTo(
                Destinations.Category.Item.Edit,
                Destinations.Category.Item.CategoryId.withValue(categoryId),
            )
        }
        .logging(logger)
}
```

Add the necessary imports at the top of `MainActivityScreenComponent.kt`:
```kotlin
import com.hluhovskyi.zero.categories.detail.CategoryDetailComponent
import com.hluhovskyi.zero.activity.screens.CategoryDetailScreen
```

- [ ] **Step 5: Create `CategoryDetailScreen`**

Create `app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoryDetailScreen.kt`:

```kotlin
package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.categories.detail.CategoryDetailComponent
import com.hluhovskyi.zero.common.AttachWithView

@Composable
internal fun CategoryDetailScreen(component: CategoryDetailComponent) {
    component.AttachWithView()
}
```

- [ ] **Step 6: Full project build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: no new errors.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoryDetailScreen.kt
git commit -m "feat: wire category detail navigation and screen"
```

---

### Task 8: UI verification on device

- [ ] **Step 1: Install on device**

```bash
./gradlew installDebug 2>&1 | tail -5
```

- [ ] **Step 2: Navigate to Categories tab, tap a category with spending**

Verify:
- Top bar shows back arrow + category name + edit icon
- Hero card has category colour background with ghosted icon, shows period label (e.g. "MAY 2026"), total amount, TRANSACTIONS count, AVG PER TX, LARGEST
- Below hero card, transaction list shows only transactions for that category (no search bar)

- [ ] **Step 3: Inspect layout bounds with `dump-ui` if hero card looks wrong**

```bash
./scripts/dump-ui.sh
```

Check that `Box(weight(1f))` around the transaction list has non-zero height (it takes the remaining space after the hero card).

- [ ] **Step 4: Tap Edit icon — verify navigation to category edit screen**

- [ ] **Step 5: Tap Back arrow — verify navigation back to category list**

- [ ] **Step 6: Run all tests one final time**

```bash
./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.
