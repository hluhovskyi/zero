# Transaction Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real-time transaction search by account name and category name using a DB-level JOIN query, with debounce and seamless switching back to the existing paginated flow when search is cleared.

**Architecture:** A new `Criteria.Search(query)` triggers a Room JOIN query across `TransactionEntity`, `AccountEntity`, and `CategoryEntity`. The ViewModel keeps both the paginated flow and the search flow subscribed simultaneously, switching the display source via `searchResult ?: paged` so paginated pages survive search round-trips. Input is debounced 300 ms in the ViewModel.

**Tech Stack:** Kotlin, kotlinx.coroutines (Flow, debounce, flatMapLatest), Room (SQL JOIN), Jetpack Compose, Mockito (tests)

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt` | Modify | Add `Criteria.Search(query: String)` |
| `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt` | Modify | Add `search()` JOIN DAO query |
| `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt` | Modify | Handle `Criteria.Search` in `query()` |
| `zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt` | Modify | Add test for Search criteria |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt` | Modify | Add `searchQuery` to State; `UpdateSearchQuery` to Action |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt` | Modify | Debounce + source-switch flow; suppress LoadMore during search |
| `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt` | Modify | Add tests for search flow switching |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` | Modify | Add SearchBar + empty state above LazyColumn |

---

## Task 1: Add `Criteria.Search` to `TransactionRepository`

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`

- [ ] **Step 1: Add the Search criteria class**

  Inside the `sealed interface Criteria<T>` block, after the existing `class CategoryUsageStatistics` line, add:

  ```kotlin
  data class Search(val query: String) : Criteria<List<Transaction>>
  ```

  The `Criteria` block should now look like:

  ```kotlin
  sealed interface Criteria<T> {
      class All : Criteria<List<Transaction>>
      data class ById(val id: Id.Known) : Criteria<Transaction>
      data class After(val dateTime: LocalDateTime) : Criteria<List<Transaction>>
      class CategoryUsageStatistics : Criteria<List<CategoryUsageStatistic>>
      data class Search(val query: String) : Criteria<List<Transaction>>
  }
  ```

- [ ] **Step 2: Verify the `Noop` implementation still compiles**

  `TransactionRepository.Noop.query()` returns `emptyFlow()` for all criteria via the generic `<T>` return — it does NOT need to be updated. Confirm by reading it:

  ```kotlin
  object Noop : TransactionRepository {
      override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
      // ...
  }
  ```

  No change needed there.

- [ ] **Step 3: Commit**

  ```
  git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt
  git commit -m "feat: add Criteria.Search to TransactionRepository"
  ```

---

## Task 2: Add `search()` JOIN query to `TransactionRoom`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`

- [ ] **Step 1: Add the JOIN query method**

  Add this method to `TransactionRoom` after the existing `selectCategoryUsageStatistic` method (before `insert`):

  ```kotlin
  @Query(
      """
      SELECT t.* FROM TransactionEntity t
      LEFT JOIN AccountEntity a ON t.accountId = a.id AND a.userId = t.userId
      LEFT JOIN CategoryEntity c ON t.categoryId = c.id AND c.userId = t.userId
      WHERE t.userId = :userId
        AND (a.name LIKE :query OR c.name LIKE :query)
      ORDER BY datetime(t.enteredDateTime) DESC
  """,
  )
  fun search(userId: String, query: String): Flow<List<TransactionEntity>>
  ```

  Notes:
  - `AccountEntity` and `CategoryEntity` are registered in the same `MainDatabase` — Room can JOIN across them.
  - No Kotlin imports are needed for the entity class names used in SQL strings.
  - The `%` wildcard wrapping (e.g., `%food%`) is added at the repository level in Task 3.

- [ ] **Step 2: Commit**

  ```
  git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt
  git commit -m "feat: add search JOIN query to TransactionRoom"
  ```

---

## Task 3: Handle `Criteria.Search` in `RoomTransactionRepository`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`
- Modify: `zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt`

- [ ] **Step 1: Write the failing test**

  In `RoomTransactionRepositoryPaginationTest`, add this test after the existing Criteria.All tests:

  ```kotlin
  @Test
  fun `Criteria_Search delegates to search DAO with percent-wrapped query`() = runTest {
      val searchFlow = MutableSharedFlow<List<TransactionEntity>>(replay = 1)
      whenever(transactionRoom.search("user1", "%food%")).thenReturn(searchFlow)

      val results = mutableListOf<List<TransactionRepository.Transaction>>()
      val job = launch {
          repo.query(TransactionRepository.Criteria.Search("food")).collect { results.add(it) }
      }

      searchFlow.emit(listOf(expenseEntity("t1", jan15h10)))
      advanceUntilIdle()
      assertEquals(listOf("t1"), results.last().map { it.id.value })

      // Simulates Room re-emitting on new matching insert
      searchFlow.emit(listOf(expenseEntity("t2", jan15h10), expenseEntity("t1", jan15h10)))
      advanceUntilIdle()
      assertEquals(listOf("t2", "t1"), results.last().map { it.id.value })

      job.cancel()
  }
  ```

- [ ] **Step 2: Run the test — expect failure**

  ```
  ./gradlew :zero-database:test --tests "com.hluhovskyi.zero.transactions.RoomTransactionRepositoryPaginationTest.Criteria_Search delegates to search DAO with percent-wrapped query"
  ```

  Expected: FAIL — `when` in `RoomTransactionRepository.query()` throws because `Criteria.Search` has no branch.

- [ ] **Step 3: Implement the Search branch in `RoomTransactionRepository`**

  In the `when (criteria)` block inside `query()`, add after the `CategoryUsageStatistics` branch:

  ```kotlin
  is TransactionRepository.Criteria.Search -> transactionRoom()
      .search(userId.value, "%${criteria.query}%")
      .map { entities -> entities.mapNotNull { it.toRepository() } }
  ```

  The full `when` block becomes:

  ```kotlin
  when (criteria) {
      is TransactionRepository.Criteria.All -> paginatedFlow(userId, trigger)
      is TransactionRepository.Criteria.After -> transactionRoom()
          .selectAfter(userId.value, criteria.dateTime.toString())
          .map { entities -> entities.mapNotNull { it.toRepository() } }
      is TransactionRepository.Criteria.ById ->
          flow<TransactionRepository.Transaction> {
              transactionRoom().selectById(criteria.id, userId)
                  ?.toRepository()
                  ?.let { transaction -> emit(transaction) }
          }
      is TransactionRepository.Criteria.CategoryUsageStatistics -> transactionRoom()
          .selectCategoryUsageStatistic(userId.value)
          .map { entities ->
              entities.map { entity ->
                  TransactionRepository.CategoryUsageStatistic(
                      categoryId = Id.Known(entity.categoryId),
                      transactionCount = entity.transactionCount,
                      lastUsedDateTime = entity.lastUsedDateTime,
                  )
              }
          }
      is TransactionRepository.Criteria.Search -> transactionRoom()
          .search(userId.value, "%${criteria.query}%")
          .map { entities -> entities.mapNotNull { it.toRepository() } }
  }
  ```

- [ ] **Step 4: Run the test — expect pass**

  ```
  ./gradlew :zero-database:test --tests "com.hluhovskyi.zero.transactions.RoomTransactionRepositoryPaginationTest.Criteria_Search delegates to search DAO with percent-wrapped query"
  ```

  Expected: PASS

- [ ] **Step 5: Run all database tests**

  ```
  ./gradlew :zero-database:test
  ```

  Expected: all tests pass.

- [ ] **Step 6: Commit**

  ```
  git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
  git add zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt
  git commit -m "feat: implement Criteria.Search in RoomTransactionRepository"
  ```

---

## Task 4: Update `TransactionViewModel` interface

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`

- [ ] **Step 1: Add `searchQuery` to `State`**

  Change:

  ```kotlin
  data class State(
      val transactions: List<Item> = emptyList(),
  )
  ```

  To:

  ```kotlin
  data class State(
      val transactions: List<Item> = emptyList(),
      val searchQuery: String = "",
  )
  ```

- [ ] **Step 2: Add `UpdateSearchQuery` to `Action`**

  Change:

  ```kotlin
  sealed interface Action {
      data class SelectTransaction(val item: Item.Transaction) : Action
      data object LoadMore : Action
  }
  ```

  To:

  ```kotlin
  sealed interface Action {
      data class SelectTransaction(val item: Item.Transaction) : Action
      data object LoadMore : Action
      data class UpdateSearchQuery(val query: String) : Action
  }
  ```

- [ ] **Step 3: Commit**

  ```
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt
  git commit -m "feat: add searchQuery state and UpdateSearchQuery action to TransactionViewModel"
  ```

---

## Task 5: Update `DefaultTransactionViewModel` with search flow

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

  Add these two tests to `DefaultTransactionViewModelTest` after the existing tests:

  ```kotlin
  @Test
  fun `UpdateSearchQuery updates searchQuery in state immediately`() = runTest {
      val viewModel = createViewModel(this)
      viewModel.attach()
      runCurrent()

      viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery("Coffee"))
      runCurrent()

      assertEquals("Coffee", viewModel.state.first().searchQuery)
  }

  @Test
  fun `UpdateSearchQuery triggers Criteria_Search query after debounce`() = runTest {
      val searchFlow = MutableSharedFlow<List<TransactionRepository.Transaction>>(replay = 1)
      whenever(transactionRepository.query(any<TransactionRepository.Criteria.Search>(), any()))
          .thenReturn(searchFlow)

      val viewModel = createViewModel(this)
      viewModel.attach()
      runCurrent()

      viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery("Coffee"))
      advanceTimeBy(299L)
      runCurrent()

      // Before debounce fires — Search should NOT have been called yet
      val criteriaBeforeDebounce = argumentCaptor<TransactionRepository.Criteria<*>>().also {
          verify(transactionRepository, atLeastOnce()).query(it.capture(), any())
      }.allValues.filterIsInstance<TransactionRepository.Criteria.Search>()
      assertEquals(0, criteriaBeforeDebounce.size)

      advanceTimeBy(1L) // debounce fires at 300ms total
      runCurrent()

      val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
      verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())
      val searchCriteria = criteriaCaptor.allValues
          .filterIsInstance<TransactionRepository.Criteria.Search>()
      assertEquals(1, searchCriteria.size)
      assertEquals("Coffee", searchCriteria.first().query)
  }

  @Test
  fun `LoadMore is no-op when search is active`() = runTest {
      val viewModel = createViewModel(this)
      viewModel.attach()
      runCurrent()

      // Capture the trigger flow before activating search
      val triggerCaptor = argumentCaptor<kotlinx.coroutines.flow.Flow<*>>()
      verify(transactionRepository, atLeastOnce()).query(
          org.mockito.kotlin.isA<TransactionRepository.Criteria.All>(),
          triggerCaptor.capture(),
      )
      val trigger = triggerCaptor.allValues.last() as MutableSharedFlow<Unit>
      val emissions = mutableListOf<Unit>()
      val collectJob = launch { trigger.collect { emissions.add(it) } }

      // Activate search, then try LoadMore
      viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery("Food"))
      advanceTimeBy(300L)
      runCurrent()
      viewModel.perform(TransactionViewModel.Action.LoadMore)
      advanceUntilIdle()

      assertEquals(0, emissions.size)
      collectJob.cancel()
  }
  ```

- [ ] **Step 2: Run the tests — expect failure**

  ```
  ./gradlew :zero-core:test --tests "com.hluhovskyi.zero.transactions.DefaultTransactionViewModelTest"
  ```

  Expected: new tests FAIL — `UpdateSearchQuery` action not handled.

- [ ] **Step 3: Add imports to `DefaultTransactionViewModel`**

  Add these imports (Kotlin deduplicates if already present):

  ```kotlin
  import kotlinx.coroutines.flow.debounce
  import kotlinx.coroutines.flow.flatMapLatest
  import kotlinx.coroutines.flow.flowOf
  import kotlinx.coroutines.flow.map
  ```

  If the compiler reports `debounce` requires an opt-in, add to the class:
  ```kotlin
  @OptIn(kotlinx.coroutines.FlowPreview::class)
  ```

- [ ] **Step 4: Handle `UpdateSearchQuery` in `perform()`**

  Add the branch to the `when` in `perform()` and guard `LoadMore` against active search:

  ```kotlin
  override fun perform(action: TransactionViewModel.Action) {
      when (action) {
          is TransactionViewModel.Action.SelectTransaction -> {
              onTransactionSelectedHandler.onSelected(action.item.id)
          }

          is TransactionViewModel.Action.LoadMore -> {
              if (mutableState.value.searchQuery.isBlank()) {
                  coroutineScope.launch {
                      loadMoreTrigger.emit(Unit)
                  }
              }
          }

          is TransactionViewModel.Action.UpdateSearchQuery -> {
              mutableState.update { it.copy(searchQuery = action.query) }
          }
      }
  }
  ```

- [ ] **Step 5: Restructure `attach()` to add search source-switching**

  Replace the entire body of `attach()` with the following. The `resolve()` private method and everything below it stays unchanged.

  ```kotlin
  override fun attach(): Closeable = Closeables.of {
      coroutineScope.launch {
          val initialTimestamp = clock.localDateTime(zoneProvider.timeZone())

          // Always subscribed — cursor/pages survive search round-trips
          val pagedTransactions = combine(
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

          // null = "no search active, use paged"; non-null = search results
          val searchTransactions = mutableState
              .map { it.searchQuery }
              .debounce(300L)
              .flatMapLatest { query ->
                  if (query.isBlank()) flowOf(null)
                  else transactionRepository.query(TransactionRepository.Criteria.Search(query))
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
  ```

- [ ] **Step 6: Run the tests — expect pass**

  ```
  ./gradlew :zero-core:test --tests "com.hluhovskyi.zero.transactions.DefaultTransactionViewModelTest"
  ```

  Expected: all tests pass.

- [ ] **Step 7: Run all core tests**

  ```
  ./gradlew :zero-core:test
  ```

  Expected: all tests pass.

- [ ] **Step 8: Commit**

  ```
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
  git add zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt
  git commit -m "feat: add search flow with debounce and source-switching to DefaultTransactionViewModel"
  ```

---

## Task 6: Add SearchBar and empty state to `TransactionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Step 1: Add missing imports**

  Add these imports to `TransactionViewProvider.kt`:

  ```kotlin
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.ui.Alignment
  import com.hluhovskyi.zero.ui.SearchBar
  ```

- [ ] **Step 2: Wrap the view with `Column` and insert `SearchBar`**

  Replace the `@Composable private fun TransactionView(...)` body with:

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
              lastVisibleIndex >= totalItems - 30
          }
      }

      LaunchedEffect(shouldLoadMore) {
          if (shouldLoadMore) {
              viewModel.perform(TransactionViewModel.Action.LoadMore)
          }
      }

      Column(modifier = Modifier.fillMaxSize()) {
          SearchBar(
              query = state.searchQuery,
              onQueryChange = { viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery(it)) },
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )

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
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              ) {
                  items(state.transactions) { transaction ->
                      when (transaction) {
                          is TransactionViewModel.Item.Summary -> {
                              Row(
                                  modifier = Modifier
                                      .fillMaxWidth()
                                      .padding(top = 20.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
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
                                  .clickable { viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction)) }
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
                              }
                          }
                      }
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 3: Build the zero-core module to catch any import/compile errors**

  ```
  ./gradlew :zero-core:assembleDebug
  ```

  Expected: BUILD SUCCESSFUL. Fix any import errors (add `Column`, `fillMaxSize`, `Alignment`, `SearchBar` if missing).

- [ ] **Step 4: Run all tests**

  ```
  ./gradlew :zero-api:test :zero-database:test :zero-core:test
  ```

  Expected: all pass.

- [ ] **Step 5: Commit**

  ```
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
  git commit -m "feat: add SearchBar and empty state to TransactionViewProvider"
  ```

---

## Final verification

- [ ] **Run the full build**

  ```
  ./gradlew assembleDebug
  ```

  Expected: BUILD SUCCESSFUL with no errors.
