package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Before
    fun setUp() {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.Transaction>>>(), any())).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.query(any<AccountRepository.Criteria>())).thenReturn(emptyFlow())
        whenever(currencyRepository.query(any<CurrencyRepository.Criteria<List<com.hluhovskyi.zero.common.Currency>>>())).thenReturn(emptyFlow())
        whenever(iconRepository.query(any<IconRepository.Criteria<List<com.hluhovskyi.zero.icons.Icon>>>())).thenReturn(emptyFlow())
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

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultTransactionViewModel(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        currencyRepository = currencyRepository,
        iconRepository = iconRepository,
        categoriesQueryUseCase = categoriesQueryUseCase,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        currencyConvertUseCase = currencyConvertUseCase,
        onTransactionSelectedHandler = onTransactionSelectedHandler,
        coroutineScope = coroutineScope
    )
}
