package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.Account
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.accounts.AccountUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class DefaultAccountDetailViewModelTest {

    @Mock private lateinit var accountUseCase: AccountUseCase

    @Mock private lateinit var spendingUseCase: AccountDetailSpendingUseCase

    private val accountId = Id.Known("acc1")
    private val fixedInstant = Instant.parse("2026-05-15T12:00:00Z")
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
    }
    private val fakeZone = object : ZoneProvider {
        override fun timeZone() = TimeZone.UTC
    }

    @Before
    fun setUp() {
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State()))
        whenever(spendingUseCase.queryForAccount(any(), any())).thenReturn(flowOf(null))
    }

    @Test
    fun `state reflects account name from accountUseCase`() = runTest {
        val account = makeAccount(name = "Chase Sapphire")
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertEquals("Chase Sapphire", vm.state.first().accountName)
    }

    @Test
    fun `state reflects balance and currencySymbol from account`() = runTest {
        val account = makeAccount(balance = Amount(BigDecimal("12480.00")), currencySymbol = "$")
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals(0, BigDecimal("12480.00").compareTo(state.balance.value))
        assertEquals("$", state.currencySymbol)
    }

    @Test
    fun `isNegativeBalance is true when balance is negative`() = runTest {
        val account = makeAccount(balance = Amount(BigDecimal("-1240.00")))
        whenever(accountUseCase.state).thenReturn(flowOf(AccountUseCase.State(accounts = listOf(account))))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertTrue(vm.state.first().isNegativeBalance)
    }

    @Test
    fun `state totalIn and totalOut come from spendingUseCase`() = runTest {
        val spending = AccountDetailSpendingUseCase.AccountSpending(
            totalIn = Amount(BigDecimal("5000.00")),
            totalOut = Amount(BigDecimal("3000.00")),
            transactionCount = 8,
        )
        whenever(spendingUseCase.queryForAccount(any(), any())).thenReturn(flowOf(spending))

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals(0, BigDecimal("5000.00").compareTo(state.totalIn.value))
        assertEquals(0, BigDecimal("3000.00").compareTo(state.totalOut.value))
        assertEquals(8, state.transactionCount)
    }

    @Test
    fun `periodDate is first day of current month`() = runTest {
        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertEquals(LocalDate(2026, 5, 1), vm.state.first().periodDate)
    }

    private fun makeAccount(
        name: String = "Test Account",
        balance: Amount = Amount.zero(),
        currencySymbol: String = "$",
    ) = Account(
        id = accountId,
        name = name,
        balance = balance,
        currencySymbol = currencySymbol,
        icon = Image.empty(),
        category = AccountCategory.BANK,
        details = null,
    )

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultAccountDetailViewModel(
        accountId = accountId,
        accountUseCase = accountUseCase,
        accountDetailSpendingUseCase = spendingUseCase,
        onBackHandler = OnBackHandler.Noop,
        clock = fakeClock,
        zoneProvider = fakeZone,
        coroutineScope = coroutineScope,
    )
}
