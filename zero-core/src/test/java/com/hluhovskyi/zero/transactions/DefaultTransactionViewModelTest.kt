package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultTransactionViewModelTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var accountRepository: AccountRepository

    @Mock private lateinit var currencyRepository: CurrencyRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase

    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase

    @Mock private lateinit var onTransactionSelectedHandler: OnTransactionSelectedHandler

    private val fixedInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
    }
    private val fakeZoneProvider = object : ZoneProvider {
        override fun timeZone() = testTimeZone
    }
    private val now: LocalDateTime = fixedInstant.toLocalDateTime(testTimeZone)

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
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        val triggerCaptor = argumentCaptor<kotlinx.coroutines.flow.Flow<*>>()
        verify(transactionRepository, atLeastOnce()).query(
            org.mockito.kotlin.isA<TransactionRepository.Criteria.All>(),
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
    fun `attach queries Criteria_After with a recent timestamp`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        val criteriaCaptor = argumentCaptor<TransactionRepository.Criteria<*>>()
        verify(transactionRepository, atLeastOnce()).query(criteriaCaptor.capture(), any())

        val afterCriteria = criteriaCaptor.allValues
            .filterIsInstance<TransactionRepository.Criteria.After>()
        assertEquals(1, afterCriteria.size)
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

        whenever(transactionRepository.query(any<TransactionRepository.Criteria.After>(), any())).thenReturn(flowOf(listOf(transaction)))
        whenever(accountRepository.query(any<AccountRepository.Criteria.All>())).thenReturn(flowOf(listOf(sourceAccount, targetAccount)))
        whenever(currencyRepository.query(any<CurrencyRepository.Criteria.All>())).thenReturn(flowOf(listOf(currency1, currency2)))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)
        whenever(currencyConvertUseCase.convertToPrimary(any(), any())).thenReturn(Amount(BigDecimal.TEN))

        val viewModel = createViewModel(backgroundScope)
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
        val viewModel = createViewModel(backgroundScope)
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

        val viewModel = createViewModel(backgroundScope)
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
        val viewModel = createViewModel(backgroundScope)
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

    @Test
    fun `DeleteTransaction action calls transactionRepository delete`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()

        viewModel.perform(TransactionViewModel.Action.DeleteTransaction(Id.Known("t1")))
        runCurrent()

        org.mockito.kotlin.verify(transactionRepository).delete(Id.Known("t1"))
    }

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
        val icon = Icon(id = Id.Known("i1"), image = Image.empty())
        val categoryIcon = Icon(id = Id.Known("i_cat"), image = Image.empty())

        // Override the setUp() stub for ForCategory queries
        val forCategoryCriteria = org.mockito.kotlin.argThat<TransactionRepository.Criteria<*>> {
            this is TransactionRepository.Criteria.ForCategory && this.categoryId == categoryId
        }
        whenever(transactionRepository.query(forCategoryCriteria, any())).thenReturn(flowOf(listOf(oneExpenseTransaction)))
        whenever(accountRepository.query(org.mockito.kotlin.isA<AccountRepository.Criteria.All>())).thenReturn(flowOf(listOf(account)))
        whenever(currencyRepository.query(org.mockito.kotlin.isA<CurrencyRepository.Criteria.All>())).thenReturn(flowOf(listOf(currency)))
        whenever(iconRepository.query(org.mockito.kotlin.isA<IconRepository.Criteria.All>())).thenReturn(flowOf(listOf(icon)))
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(
            flowOf(
                listOf(
                    CategoriesQueryUseCase.Category(categoryId, "Food", categoryIcon.image, ColorScheme.Grey),
                ),
            ),
        )
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(currency)
        whenever(currencyConvertUseCase.convertToPrimary(any(), any())).thenReturn(Amount(BigDecimal.TEN))

        val viewModel = createViewModel(backgroundScope, filter = TransactionFilter.ForCategory(categoryId))
        viewModel.attach()
        runCurrent()
        advanceUntilIdle()

        val state = viewModel.state.first()
        val transactionItems = state.transactions.filterIsInstance<TransactionViewModel.Item.Transaction>()

        assertEquals("Expected 1 transaction but got ${transactionItems.size}", 1, transactionItems.size)
        assertEquals("Transaction ID mismatch", oneExpenseTransaction.id, transactionItems.first().id)
    }

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
}
