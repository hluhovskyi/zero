package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
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
        val viewModel = createViewModel(this)
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
        val viewModel = createViewModel(this)
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

        val viewModel = createViewModel(this)
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

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultTransactionViewModel(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        currencyRepository = currencyRepository,
        iconRepository = iconRepository,
        categoriesQueryUseCase = categoriesQueryUseCase,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        currencyConvertUseCase = currencyConvertUseCase,
        onTransactionSelectedHandler = onTransactionSelectedHandler,
        clock = fakeClock,
        zoneProvider = fakeZoneProvider,
        coroutineScope = coroutineScope,
    )
}
