# Transaction Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the all-at-once transaction load with cursor-based pagination that loads ~20 transactions at startup and fetches more pages as the user scrolls down, while surfacing newly added transactions reactively via a dedicated `Criteria.After` query.

**Architecture:** Two queries cooperate in the ViewModel. `Criteria.After(initialTimestamp)` is a reactive Room `Flow` that emits whenever a new transaction is inserted after the session started. `Criteria.All()` with a `trigger: Flow<*>` uses one-shot suspend Room queries to load cursor-based history pages. The ViewModel pre-combines the two lists (deduped by id) before feeding into the existing enrichment `combine()`. A `MutableSharedFlow<Unit>` inside the ViewModel is the trigger; `perform(Action.LoadMore)` emits to it. The UI dispatches `LoadMore` when the user scrolls near the bottom via `LazyListState`.

**Tech Stack:** Kotlin, Kotlin Coroutines + Flow, Room (SQLite), Jetpack Compose, Mockito

---

## Files

**Modify:**
- `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt` — add `trigger` param to `query()`, add `Criteria.After`, update `Noop`
- `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt` — add reactive `selectAfter` + two suspend page queries
- `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt` — implement `Criteria.After` and cursor-based `Criteria.All` with trigger
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt` — add `LoadMore` to `Action`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt` — add trigger, `initialTimestamp`, pre-combine two transaction flows, handle `LoadMore`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` — add scroll detection + `LaunchedEffect`

---

### Task 1: Extend `TransactionRepository` interface

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`

- [ ] **Step 1: Add `trigger` param and `Criteria.After`**

```kotlin
// Replace existing query():
fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow()): Flow<T>

// Add inside Criteria sealed interface, after existing entries:
data class After(val dateTime: LocalDateTime) : Criteria<List<Transaction>>

// Update Noop:
object Noop : TransactionRepository {
    override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
    override suspend fun insert(transaction: Transaction) = Unit
    override suspend fun insert(transactions: List<Transaction>) = Unit
}
```

`LocalDateTime` is already imported. `emptyFlow()` is already imported.

- [ ] **Step 2: Verify compile**

```bash
./gradlew :zero-api:compileDebugKotlin :zero-core:compileDebugKotlin :zero-database:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL — default value means no existing call site needs updating.

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt
git commit -m "feat: add trigger param and Criteria.After to TransactionRepository"
```

---

### Task 2: Add Room DAO queries

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`

- [ ] **Step 1: Add the three new queries**

Add after the existing `selectByUserId` methods:

```kotlin
// Reactive — Room re-emits when any matching row is inserted/updated/deleted
// after: ISO datetime string e.g. "2024-01-15T10:30:00"
@Query("""
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND datetime(enteredDateTime) > datetime(:after)
    ORDER BY datetime(enteredDateTime) DESC
""")
fun selectAfter(userId: String, after: String): Flow<List<TransactionEntity>>

// One-shot — first page, most recent :limit transactions
@Query("""
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
    ORDER BY datetime(enteredDateTime) DESC
    LIMIT :limit
""")
suspend fun selectFirstPage(userId: String, limit: Int): List<TransactionEntity>

// One-shot — next cursor page, strictly before cursorDate "YYYY-MM-DD"
@Query("""
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND date(enteredDateTime) < date(:cursorDate)
    ORDER BY datetime(enteredDateTime) DESC
    LIMIT :limit
""")
suspend fun selectNextPage(userId: String, cursorDate: String, limit: Int): List<TransactionEntity>

// One-shot — all transactions on :day older than :beforeDateTime (day padding)
// day: "YYYY-MM-DD", beforeDateTime: ISO datetime string
@Query("""
    SELECT * FROM TransactionEntity
    WHERE userId = :userId
      AND date(enteredDateTime) = date(:day)
      AND datetime(enteredDateTime) < datetime(:beforeDateTime)
    ORDER BY datetime(enteredDateTime) DESC
""")
suspend fun selectRemainingOnDay(
    userId: String,
    day: String,
    beforeDateTime: String,
): List<TransactionEntity>
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :zero-database:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt
git commit -m "feat: add selectAfter, selectFirstPage, selectNextPage, selectRemainingOnDay to TransactionRoom"
```

---

### Task 3: Implement `Criteria.After` and paginated `Criteria.All` in `RoomTransactionRepository`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`
- Create: `zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@RunWith(MockitoJUnitRunner::class)
class RoomTransactionRepositoryPaginationTest {

    @Mock private lateinit var transactionRoom: TransactionRoom
    @Mock private lateinit var incorrectStateDetector: IncorrectStateDetector
    @Mock private lateinit var clock: Clock

    private lateinit var repo: RoomTransactionRepository

    private val userId = Id.Known("user1")
    private val jan15_10h = LocalDateTime.of(2024, 1, 15, 10, 0)
    private val jan15_8h  = LocalDateTime.of(2024, 1, 15, 8, 0)
    private val jan14_18h = LocalDateTime.of(2024, 1, 14, 18, 0)

    @Before
    fun setUp() {
        repo = RoomTransactionRepository(
            transactionRoom = { transactionRoom },
            currentUserId = flowOf(userId),
            incorrectStateDetector = incorrectStateDetector,
            clock = clock,
        )
    }

    private fun expenseEntity(id: String, dateTime: LocalDateTime) = TransactionEntity(
        id = Id.Known(id),
        userId = userId,
        type = TransactionEntity.Type.EXPENSE,
        currencyId = Id.Known("usd"),
        accountId = Id.Known("acc"),
        categoryId = "cat",
        amount = AmountEntity(java.math.BigDecimal.ONE),
        rate = RateEntity(java.math.BigDecimal.ONE),
        targetAccount = null,
        targetAmount = AmountEntity.empty(),
        enteredDateTime = dateTime,
        creationDateTime = dateTime,
        updatedDateTime = dateTime,
    )

    // --- Criteria.After ---

    @Test
    fun `Criteria_After emits transactions newer than given datetime`() = runTest {
        val roomFlow = MutableSharedFlow<List<TransactionEntity>>(replay = 1)
        whenever(transactionRoom.selectAfter("user1", jan15_8h.toString()))
            .thenReturn(roomFlow)

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.After(jan15_8h)).collect { results.add(it) }
        }

        roomFlow.emit(listOf(expenseEntity("t1", jan15_10h)))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        // Simulate new insert — Room re-emits
        roomFlow.emit(listOf(expenseEntity("t2", jan15_10h.plusHours(1)), expenseEntity("t1", jan15_10h)))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("t2", "t1"), results.last().map { it.id.value })

        job.cancel()
    }

    // --- Criteria.All with trigger ---

    @Test
    fun `Criteria_All initial page is padded to complete the last day`() = runTest {
        whenever(transactionRoom.selectFirstPage("user1", 20))
            .thenReturn(listOf(expenseEntity("t1", jan15_10h)))
        whenever(transactionRoom.selectRemainingOnDay("user1", "2024-01-15", jan15_10h.toString()))
            .thenReturn(listOf(expenseEntity("t2", jan15_8h)))

        val result = repo.query(TransactionRepository.Criteria.All()).first()

        assertEquals(listOf("t1", "t2"), result.map { it.id.value })
    }

    @Test
    fun `Criteria_All trigger loads next cursor page appended to first`() = runTest {
        val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        whenever(transactionRoom.selectFirstPage("user1", 20))
            .thenReturn(listOf(expenseEntity("t1", jan15_10h)))
        whenever(transactionRoom.selectRemainingOnDay(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(transactionRoom.selectNextPage("user1", "2024-01-15", 20))
            .thenReturn(listOf(expenseEntity("t2", jan14_18h)))

        val results = mutableListOf<List<TransactionRepository.Transaction>>()
        val job = launch {
            repo.query(TransactionRepository.Criteria.All(), trigger).collect { results.add(it) }
        }

        testScheduler.advanceUntilIdle()
        assertEquals(listOf("t1"), results.last().map { it.id.value })

        trigger.emit(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("t1", "t2"), results.last().map { it.id.value })

        job.cancel()
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :zero-database:testDebugUnitTest --tests "*.RoomTransactionRepositoryPaginationTest"
```

Expected: FAILED.

- [ ] **Step 3: Implement `Criteria.After` and paginated `Criteria.All`**

Replace the existing `query()` method and add helpers:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
override fun <T> query(
    criteria: TransactionRepository.Criteria<T>,
    trigger: Flow<*>,
): Flow<T> = currentUserId.take(1)
    .flatMapConcat { userId ->
        when (criteria) {
            is TransactionRepository.Criteria.All -> paginatedFlow(userId, trigger)
            is TransactionRepository.Criteria.After -> transactionRoom()
                .selectAfter(userId.value, criteria.dateTime.toString())
                .map { entities -> entities.mapNotNull { it.toRepository() } }
            is TransactionRepository.Criteria.ById ->
                flow<TransactionRepository.Transaction> {
                    transactionRoom().selectById(criteria.id, userId)
                        ?.toRepository()
                        ?.let { emit(it) }
                }
        }
    }
    .uncheckedCast()

private fun paginatedFlow(
    userId: Id.Known,
    trigger: Flow<*>,
): Flow<List<TransactionRepository.Transaction>> = flow {
    val accumulated = mutableListOf<TransactionEntity>()

    val firstPage = transactionRoom().selectFirstPage(userId.value, PAGE_SIZE)
    accumulated.addAll(firstPage + loadDayPadding(userId, firstPage))
    emit(accumulated.mapNotNull { it.toRepository() })

    trigger.collect {
        val oldest = accumulated.lastOrNull() ?: return@collect
        val cursorDate = oldest.enteredDateTime.toLocalDate().toString()
        val nextPage = transactionRoom().selectNextPage(userId.value, cursorDate, PAGE_SIZE)
        if (nextPage.isEmpty()) return@collect
        accumulated.addAll(nextPage + loadDayPadding(userId, nextPage))
        emit(accumulated.mapNotNull { it.toRepository() })
    }
}

private suspend fun loadDayPadding(
    userId: Id.Known,
    page: List<TransactionEntity>,
): List<TransactionEntity> {
    val oldest = page.lastOrNull() ?: return emptyList()
    return transactionRoom().selectRemainingOnDay(
        userId = userId.value,
        day = oldest.enteredDateTime.toLocalDate().toString(),
        beforeDateTime = oldest.enteredDateTime.toString(),
    )
}

private companion object {
    const val PAGE_SIZE = 20
}
```

Add imports:

```kotlin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :zero-database:testDebugUnitTest --tests "*.RoomTransactionRepositoryPaginationTest"
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
git add zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt
git commit -m "feat: implement Criteria.After and cursor-based paginated flow in RoomTransactionRepository"
```

---

### Task 4: Add `LoadMore` action and wire trigger + `Criteria.After` in `DefaultTransactionViewModel`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`
- Create: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelLoadMoreTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class DefaultTransactionViewModelLoadMoreTest {

    @Mock private lateinit var transactionRepository: TransactionRepository
    @Mock private lateinit var accountRepository: AccountRepository
    @Mock private lateinit var currencyRepository: CurrencyRepository
    @Mock private lateinit var iconRepository: IconRepository
    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase
    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase
    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase
    @Mock private lateinit var onTransactionSelectedHandler: OnTransactionSelectedHandler

    private lateinit var viewModel: DefaultTransactionViewModel

    @Before
    fun setUp() {
        whenever(transactionRepository.query(any(), any())).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.query(any())).thenReturn(emptyFlow())
        whenever(currencyRepository.query(any())).thenReturn(emptyFlow())
        whenever(iconRepository.query(any())).thenReturn(emptyFlow())
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(emptyFlow())

        viewModel = DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
            onTransactionSelectedHandler = onTransactionSelectedHandler,
        )
        viewModel.attach()
    }

    @Test
    fun `LoadMore action emits to the trigger passed to Criteria_All query`() = runTest {
        val triggerCaptor = argumentCaptor<kotlinx.coroutines.flow.Flow<*>>()
        verify(transactionRepository).query(
            org.mockito.kotlin.isA<TransactionRepository.Criteria.All>(),
            triggerCaptor.capture(),
        )
        val trigger = triggerCaptor.firstValue as MutableSharedFlow<*>

        val emissions = mutableListOf<Any>()
        val collectJob = launch { trigger.collect { emissions.add(it) } }

        viewModel.perform(TransactionViewModel.Action.LoadMore)
        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        collectJob.cancel()
    }

    @Test
    fun `attach queries Criteria_After with a recent timestamp`() = runTest {
        val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
        verify(transactionRepository, org.mockito.kotlin.atLeastOnce()).query(criteriaCaptor.capture(), any())

        val afterCriteria = criteriaCaptor.allValues
            .filterIsInstance<TransactionRepository.Criteria.After>()
        assertEquals(1, afterCriteria.size)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultTransactionViewModelLoadMoreTest"
```

Expected: FAILED — `LoadMore` doesn't exist, `Criteria.After` not used.

- [ ] **Step 3: Add `LoadMore` to `TransactionViewModel.Action`**

```kotlin
sealed interface Action {
    data class SelectTransaction(val item: Item.Transaction) : Action
    data object LoadMore : Action
}
```

- [ ] **Step 4: Update `DefaultTransactionViewModel`**

Add fields alongside `mutableState`:

```kotlin
private val loadMoreTrigger = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

Replace `perform()`:

```kotlin
override fun perform(action: TransactionViewModel.Action) {
    when (action) {
        is TransactionViewModel.Action.SelectTransaction -> {
            onTransactionSelectedHandler.onSelected(action.item.id)
        }
        is TransactionViewModel.Action.LoadMore -> {
            coroutineScope.launch {
                loadMoreTrigger.emit(Unit)
            }
        }
    }
}
```

In `attach()`, replace the single `transactionRepository.query(...)` with a pre-combined flow of two queries. Replace:

```kotlin
transactionRepository.query(TransactionRepository.Criteria.All())
    .onStartWithEmptyList()
    .onEmptyReturnEmptyList(),
```

With:

```kotlin
val initialTimestamp = LocalDateTime.now()
combine(
    transactionRepository.query(TransactionRepository.Criteria.After(initialTimestamp))
        .onStartWithEmptyList()
        .onEmptyReturnEmptyList(),
    transactionRepository.query(TransactionRepository.Criteria.All(), trigger = loadMoreTrigger)
        .onStartWithEmptyList()
        .onEmptyReturnEmptyList(),
) { new, paged ->
    (new + paged).distinctBy { it.id }
},
```

Add imports:

```kotlin
import java.time.LocalDateTime
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :zero-core:testDebugUnitTest --tests "*.DefaultTransactionViewModelLoadMoreTest"
```

Expected: all tests PASS.

- [ ] **Step 6: Compile check**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
git add zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelLoadMoreTest.kt
git commit -m "feat: add LoadMore, Criteria.After subscription, and trigger wiring to DefaultTransactionViewModel"
```

---

### Task 5: Detect scroll near bottom and dispatch `LoadMore`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Step 1: Add scroll detection to `TransactionView`**

Replace the `TransactionView` composable signature and opening with:

```kotlin
@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())
    val lazyListState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.perform(TransactionViewModel.Action.LoadMore)
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ... existing items block unchanged ...
    }
}
```

Add imports:

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
```

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: trigger LoadMore when user scrolls near bottom of transaction list"
```

---

## Manual Verification Checklist

- [ ] App opens showing only ~20 most recent transactions (not all)
- [ ] Adding a new transaction via FAB immediately appears at the top (Criteria.After reactive)
- [ ] Scrolling near the bottom triggers next page load automatically
- [ ] Each date group header shows the correct daily total (no partial-day summaries)
- [ ] Scrolling through 100+ transactions stays smooth throughout
