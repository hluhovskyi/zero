package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconCategory
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionViewModel.FilterSummary.Emphasis
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class DefaultTransactionViewModelTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var accountRepository: AccountRepository

    @Mock private lateinit var currencyRepository: CurrencyRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var colorRepository: ColorRepository

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase

    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase

    @Mock private lateinit var onTransactionSelectedHandler: OnTransactionSelectedHandler

    @Mock private lateinit var onDuplicateTransactionHandler: OnDuplicateTransactionHandler

    private val fixedInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val now: LocalDateTime = fixedInstant.toLocalDateTime(testTimeZone)
    private val fakeZonedClock = object : ZonedClock {
        override fun now() = fixedInstant
        override fun timeZone() = testTimeZone
    }

    @Before
    fun setUp() {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.Transaction>>>(), any())).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.query(any<AccountRepository.Criteria>())).thenReturn(emptyFlow())
        whenever(currencyRepository.query(any<CurrencyRepository.Criteria<List<Currency>>>())).thenReturn(emptyFlow())
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>())).thenReturn(emptyFlow())
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(emptyFlow())
    }

    @Test
    fun `LoadMore action emits to the trigger passed to Criteria_All query`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        val triggerCaptor = argumentCaptor<kotlinx.coroutines.flow.Flow<*>>()
        verify(transactionRepository, atLeastOnce()).query(
            isA<TransactionRepository.Criteria.All>(),
            triggerCaptor.capture(),
        )
        val trigger = triggerCaptor.allValues.last() as MutableSharedFlow<Unit>

        val emissions = mutableListOf<Unit>()
        val collectJob = launch {
            trigger.collect {
                emissions.add(it)
            }
        }

        viewModel.perform(TransactionViewModel.Action.LoadMore)
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        collectJob.cancel()
    }

    @Test
    fun `attach maps Transfer repository items to ViewModel Transfer items`() = runTest {
        val transaction = TransactionRepository.Transaction.Transfer(
            id = Id.Known("t1"),
            dateTime = now,
            updatedDateTime = now,
            amount = Amount(BigDecimal.TEN),
            currencyId = Id.Known("c1"),
            accountId = Id.Known("a1"),
            targetAccount = Id.Known("a2"),
            targetAmount = Amount(BigDecimal.valueOf(20)),
        )
        val sourceAccount = AccountRepository.Account(
            id = Id.Known("a1"),
            name = "Source",
            currencyId = Id.Known("c1"),
            iconId = Id.Known("i1"),
            initialBalance = Amount.zero(),
            category = AccountCategory.OTHER,
            details = null,
        )
        val targetAccount = AccountRepository.Account(
            id = Id.Known("a2"),
            name = "Target",
            currencyId = Id.Known("c2"),
            iconId = Id.Known("i2"),
            initialBalance = Amount.zero(),
            category = AccountCategory.OTHER,
            details = null,
        )
        val currency1 = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
        val currency2 = Currency(id = Id.Known("c2"), name = "Euro", symbol = "€")
        val primaryCurrency = currency1

        whenever(transactionRepository.query(any<TransactionRepository.Criteria.All>(), any())).thenReturn(flowOf(listOf(transaction)))
        whenever(accountRepository.query(any<AccountRepository.Criteria.All>())).thenReturn(flowOf(listOf(sourceAccount, targetAccount)))
        whenever(currencyRepository.query(any<CurrencyRepository.Criteria.All>())).thenReturn(flowOf(listOf(currency1, currency2)))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)
        whenever(currencyConvertUseCase.convertToPrimary(any(), any())).thenReturn(Amount(BigDecimal.TEN))

        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        val state = viewModel.state.first()
        val transfer = state.transactions.filterIsInstance<TransactionViewModel.Item.Transaction.Transfer>().first()

        assertEquals(transaction.id, transfer.id)
        assertEquals(sourceAccount.name, transfer.accountName)
        assertEquals(targetAccount.name, transfer.targetAccountName)
        assertEquals(transaction.amount, transfer.amount)
        assertEquals(transaction.targetAmount, transfer.targetAmount)
        assertEquals(currency1.symbol, transfer.currencySymbol)
        assertEquals(currency2.symbol, transfer.targetCurrencySymbol)
        assertEquals(ColorScheme.Grey, transfer.transferColorScheme)
    }

    @Test
    fun `UpdateSearchQuery updates searchQuery in state immediately`() = runTest {
        val viewModel = createViewModel(testScheduler)
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

        val viewModel = createViewModel(testScheduler)
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
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        // Capture the trigger flow before activating search
        val triggerCaptor = argumentCaptor<kotlinx.coroutines.flow.Flow<*>>()
        verify(transactionRepository, atLeastOnce()).query(
            isA<TransactionRepository.Criteria.All>(),
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

    @Test
    fun `ToggleSelection adds then removes an id and exits selection mode when empty`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        assertEquals(setOf(Id.Known("t1")), viewModel.state.first().selectedIds)
        assertEquals(true, viewModel.state.first().inSelectionMode)

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
        assertEquals(false, viewModel.state.first().inSelectionMode)
    }

    @Test
    fun `DeleteSelected deletes every selected id and clears the selection`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t2")))
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.DeleteSelected)
        runCurrent()

        verify(transactionRepository).delete(Id.Known("t1"))
        verify(transactionRepository).delete(Id.Known("t2"))
        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
    }

    @Test
    fun `DuplicateSelected duplicates the single selected transaction and clears selection`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        viewModel.perform(TransactionViewModel.Action.DuplicateSelected)
        runCurrent()

        verify(onDuplicateTransactionHandler).onDuplicate(Id.Known("t1"))
        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
    }

    @Test
    fun `ExitSelection clears the selection`() = runTest {
        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.ToggleSelection(Id.Known("t1")))
        runCurrent()
        viewModel.perform(TransactionViewModel.Action.ExitSelection)
        runCurrent()

        assertEquals(emptySet<Id.Known>(), viewModel.state.first().selectedIds)
    }

    @Test
    fun `ForCategory filter queries ForCategories criterion instead of All`() = runTest {
        val categoryId = Id.Known("cat1")
        val viewModel = createViewModel(testScheduler, filter = TransactionFilter.forCategory(categoryId))
        viewModel.attach()
        runCurrent()

        val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
        verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())

        val forCat = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.ForCategories>()
        assertEquals(1, forCat.size)
        assertEquals(setOf(categoryId), forCat.first().categoryIds)

        val allCriteria = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.All>()
        assertEquals(0, allCriteria.size)
    }

    @Test
    fun `ForCategory filter makes LoadMore a no-op`() = runTest {
        val categoryId = Id.Known("cat1")
        val viewModel = createViewModel(testScheduler, filter = TransactionFilter.forCategory(categoryId))
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

    @Test
    fun `ForCategory filter flows transactions into state`() = runTest {
        val categoryId = Id.Known("cat1")
        val oneExpenseTransaction = TransactionRepository.Transaction.Expense(
            id = Id.Known("t1"),
            dateTime = now,
            updatedDateTime = now,
            amount = Amount(BigDecimal.TEN),
            currencyId = Id.Known("c1"),
            accountId = Id.Known("a1"),
            categoryId = categoryId,
            rate = Rate.Same,
        )
        val account = AccountRepository.Account(
            id = Id.Known("a1"),
            name = "Checking",
            currencyId = Id.Known("c1"),
            iconId = Id.Known("i1"),
            initialBalance = Amount.zero(),
            category = AccountCategory.OTHER,
            details = null,
        )
        val currency = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
        val icon = Icon(id = Id.Known("i1"), image = Image.empty(), category = IconCategory.unknown())
        val categoryIcon = Icon(id = Id.Known("i_cat"), image = Image.empty(), category = IconCategory.unknown())

        // Override the setUp() stub for ForCategories queries
        val forCategoryCriteria = argThat<TransactionRepository.Criteria<*>> {
            this is TransactionRepository.Criteria.ForCategories && this.categoryIds == setOf(categoryId)
        }
        whenever(transactionRepository.query(forCategoryCriteria, any())).thenReturn(flowOf(listOf(oneExpenseTransaction)))
        whenever(accountRepository.query(isA<AccountRepository.Criteria.All>())).thenReturn(flowOf(listOf(account)))
        whenever(currencyRepository.query(isA<CurrencyRepository.Criteria.All>())).thenReturn(flowOf(listOf(currency)))
        whenever(iconRepository.query(isA<IconRepository.Criteria.All>())).thenReturn(flowOf(listOf(icon)))
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(
            flowOf(
                listOf(
                    CategoriesQueryUseCase.Category(categoryId, "Food", categoryIcon.image, ColorScheme.Grey),
                ),
            ),
        )
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)
        whenever(currencyConvertUseCase.convertToPrimary(any(), any())).thenReturn(Amount(BigDecimal.TEN))

        val viewModel = createViewModel(testScheduler, filter = TransactionFilter.forCategory(categoryId))
        viewModel.attach()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.state.first()
        val transactionItems = state.transactions.filterIsInstance<TransactionViewModel.Item.Transaction>()

        assertEquals("Expected 1 transaction but got ${transactionItems.size}", 1, transactionItems.size)
        assertEquals("Transaction ID mismatch", oneExpenseTransaction.id, transactionItems.first().id)
    }

    @Test
    fun `active filter is resolved to a Criteria_Filtered SQL query`() = runTest {
        // fakeZonedClock is fixed at 2024-06-01, so ThisMonth resolves to 2024-06-01..2024-06-01.
        val viewModel = createViewModel(
            testScheduler,
            transactionFilterUseCase = appliedFilter(
                TransactionFilter(
                    period = TransactionFilter.DatePeriod.ThisMonth,
                    type = TransactionFilter.TransactionType.Income,
                    categoryIds = setOf(Id.Known("cat1")),
                ),
            ),
        )
        viewModel.attach()
        runCurrent()
        advanceUntilIdle()

        val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
        verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())

        val filtered = criteriaCaptor.allValues.filterIsInstance<TransactionRepository.Criteria.Filtered>()
        assertEquals(1, filtered.size)
        assertEquals(TransactionRepository.Type.Income, filtered.first().type)
        assertEquals(setOf(Id.Known("cat1")), filtered.first().categoryIds)
        assertEquals(LocalDate(2024, 6, 1), filtered.first().from)
        assertEquals(LocalDate(2024, 6, 1), filtered.first().to)
    }

    @Test
    fun `filter summary on search shows Net Out In for mixed transactions`() = runTest {
        val summary = searchSummary(listOf(income("t1", 100), expense("t2", 30)))!!

        assertEquals(2, summary.count)
        assertEquals(3, summary.columns.size)
        assertColumn(summary.columns[0], TransactionViewModel.FilterSummary.Label.Net, 70L, Emphasis.Positive)
        assertColumn(summary.columns[1], TransactionViewModel.FilterSummary.Label.Out, 30L, Emphasis.Neutral)
        assertColumn(summary.columns[2], TransactionViewModel.FilterSummary.Label.In, 100L, Emphasis.Positive)
    }

    @Test
    fun `filter summary net is a magnitude with negative emphasis when spending exceeds income`() = runTest {
        val summary = searchSummary(listOf(income("t1", 30), expense("t2", 100)))!!

        // Magnitude (70), not -70 — the card prepends the sign, so a signed value would double up.
        assertColumn(summary.columns[0], TransactionViewModel.FilterSummary.Label.Net, 70L, Emphasis.Negative)
    }

    @Test
    fun `filter summary with no income shows Spent Avg Largest`() = runTest {
        val summary = searchSummary(listOf(expense("t1", 40), expense("t2", 20)))!!

        assertColumn(summary.columns[0], TransactionViewModel.FilterSummary.Label.Spent, 60L, Emphasis.Neutral)
        assertColumn(summary.columns[1], TransactionViewModel.FilterSummary.Label.Avg, 30L, Emphasis.Neutral)
        assertColumn(summary.columns[2], TransactionViewModel.FilterSummary.Label.Largest, 40L, Emphasis.Neutral)
    }

    @Test
    fun `filter summary with no expense shows Received Avg Largest`() = runTest {
        val summary = searchSummary(listOf(income("t1", 80), income("t2", 20)))!!

        assertColumn(summary.columns[0], TransactionViewModel.FilterSummary.Label.Received, 100L, Emphasis.Positive)
        assertColumn(summary.columns[1], TransactionViewModel.FilterSummary.Label.Avg, 50L, Emphasis.Neutral)
        assertColumn(summary.columns[2], TransactionViewModel.FilterSummary.Label.Largest, 80L, Emphasis.Neutral)
    }

    @Test
    fun `filter summary with only transfers shows zero net and faint placeholders`() = runTest {
        val summary = searchSummary(listOf(transfer("t1")))!!

        assertEquals(1, summary.count)
        assertColumn(summary.columns[0], TransactionViewModel.FilterSummary.Label.Net, 0L, Emphasis.Neutral)
        assertEquals(TransactionViewModel.FilterSummary.Label.Out, summary.columns[1].label)
        assertEquals(null, summary.columns[1].amount)
        assertEquals(Emphasis.Faint, summary.columns[1].emphasis)
        assertEquals(null, summary.columns[2].amount)
        assertEquals(Emphasis.Faint, summary.columns[2].emphasis)
    }

    @Test
    fun `no filter summary when neither search nor filter is active`() = runTest {
        stubReferenceData()
        whenever(transactionRepository.query(isA<TransactionRepository.Criteria.All>(), any()))
            .thenReturn(flowOf(listOf(expense("t1", 40))))

        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.filterSummary)
    }

    private fun appliedFilter(filter: TransactionFilter) = object : TransactionFilterUseCase {
        override val pendingFilter: Flow<TransactionFilter> = flowOf(TransactionFilter())
        override val state: Flow<TransactionFilterUseCase.State> =
            flowOf(TransactionFilterUseCase.State.Applied(filter))

        override fun perform(action: TransactionFilterUseCase.Action) = Unit
    }

    private suspend fun TestScope.searchSummary(
        transactions: List<TransactionRepository.Transaction>,
    ): TransactionViewModel.FilterSummary? {
        stubReferenceData()
        whenever(transactionRepository.query(isA<TransactionRepository.Criteria.Search>(), any()))
            .thenReturn(flowOf(transactions))

        val viewModel = createViewModel(testScheduler)
        viewModel.attach()
        runCurrent()
        viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery("x"))
        advanceTimeBy(300L)
        advanceUntilIdle()
        return viewModel.state.value.filterSummary
    }

    private fun assertColumn(
        column: TransactionViewModel.FilterSummary.Column,
        label: TransactionViewModel.FilterSummary.Label,
        value: Long,
        emphasis: Emphasis,
    ) {
        assertEquals(label, column.label)
        assertEquals(value, column.amount?.value?.toLong())
        assertEquals(emphasis, column.emphasis)
    }

    private suspend fun stubReferenceData() {
        whenever(accountRepository.query(isA<AccountRepository.Criteria.All>()))
            .thenReturn(flowOf(listOf(summaryAccount, summaryAccount2)))
        whenever(currencyRepository.query(isA<CurrencyRepository.Criteria.All>()))
            .thenReturn(flowOf(listOf(summaryCurrency)))
        whenever(iconRepository.query(isA<IconRepository.Criteria.All>()))
            .thenReturn(flowOf(listOf(summaryIcon)))
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(flowOf(listOf(summaryCategory)))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(summaryCurrency)
        whenever(currencyConvertUseCase.convertToPrimary(any(), any()))
            .thenAnswer { it.getArgument<Amount>(0) }
    }

    private val summaryCurrency = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
    private val summaryAccount = AccountRepository.Account(
        id = Id.Known("a1"), name = "Checking", currencyId = Id.Known("c1"),
        iconId = Id.Known("i1"), initialBalance = Amount.zero(), category = AccountCategory.OTHER, details = null,
    )
    private val summaryAccount2 = AccountRepository.Account(
        id = Id.Known("a2"), name = "Savings", currencyId = Id.Known("c1"),
        iconId = Id.Known("i1"), initialBalance = Amount.zero(), category = AccountCategory.OTHER, details = null,
    )
    private val summaryIcon = Icon(id = Id.Known("i1"), image = Image.empty(), category = IconCategory.unknown())
    private val summaryCategory = CategoriesQueryUseCase.Category(Id.Known("cat1"), "Food", Image.empty(), ColorScheme.Grey)

    private fun expense(id: String, amount: Long) = TransactionRepository.Transaction.Expense(
        id = Id.Known(id), dateTime = now, updatedDateTime = now, amount = Amount(BigDecimal.valueOf(amount)),
        currencyId = Id.Known("c1"), accountId = Id.Known("a1"), categoryId = Id.Known("cat1"), rate = Rate.Same,
    )

    private fun income(id: String, amount: Long) = TransactionRepository.Transaction.Income(
        id = Id.Known(id), dateTime = now, updatedDateTime = now, amount = Amount(BigDecimal.valueOf(amount)),
        currencyId = Id.Known("c1"), accountId = Id.Known("a1"), categoryId = Id.Known("cat1"), rate = Rate.Same,
    )

    private fun transfer(id: String) = TransactionRepository.Transaction.Transfer(
        id = Id.Known(id), dateTime = now, updatedDateTime = now, amount = Amount(BigDecimal.TEN),
        currencyId = Id.Known("c1"), accountId = Id.Known("a1"), targetAccount = Id.Known("a2"),
        targetAmount = Amount(BigDecimal.TEN),
    )

    private fun createViewModel(
        scheduler: TestCoroutineScheduler,
        filter: TransactionFilter = TransactionFilter.All,
        transactionFilterUseCase: TransactionFilterUseCase = TransactionFilterUseCase.Noop,
    ): DefaultTransactionViewModel {
        val dispatcher = StandardTestDispatcher(scheduler)
        val dispatchers = object : DispatcherProvider {
            override fun io(): CoroutineDispatcher = dispatcher
            override fun cpu(): CoroutineDispatcher = dispatcher
            override fun main(): CoroutineDispatcher = dispatcher
        }
        return DefaultTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            currencyConvertUseCase = currencyConvertUseCase,
            onTransactionSelectedHandler = onTransactionSelectedHandler,
            onDuplicateTransactionHandler = onDuplicateTransactionHandler,
            filter = filter,
            transactionFilterUseCase = transactionFilterUseCase,
            zonedClock = fakeZonedClock,
            dispatchers = dispatchers,
        )
    }
}
