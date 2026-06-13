package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCashFlowReportViewModelTest {

    private val report = MutableStateFlow(sampleReport())
    private val fakeUseCase = object : CashFlowReportUseCase {
        override fun query(current: DateRange, prior: DateRange): Flow<CashFlowReportUseCase.Report> = report
    }

    private val fakeClock = object : ZonedClock {
        override fun now() = Instant.parse("2026-04-15T12:00:00Z")
        override fun timeZone() = TimeZone.UTC
    }
    private val fakeCurrency = object : CurrencyPrimaryUseCase {
        override suspend fun getPrimaryCurrency() = Currency(id = Id.Known("usd"), name = "US Dollar", symbol = "$")
        override suspend fun setPrimaryCurrency(id: Id.Known) = Unit
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `net is in minus out`() = runTest {
        assertEquals(Amount(6810L), attached().net)
    }

    @Test fun `savings rate is net over income, rounded`() = runTest {
        assertEquals(22, attached().savingsRate)
    }

    @Test fun `savings trend is per-month savings rate with range`() = runTest {
        val cashFlow = attached()
        assertEquals(listOf(21f, 14f, 28f, 23f, 21f, 26f), cashFlow.savingsTrend)
        assertEquals(14, cashFlow.savingsRateMin)
        assertEquals(28, cashFlow.savingsRateMax)
    }

    @Test fun `latest month is the newest bucket`() = runTest {
        val latest = attached().latest!!
        assertEquals("Apr", latest.label)
        assertEquals(5050f, latest.income)
        assertEquals(3760f, latest.expense)
    }

    @Test fun `income shares are each source over total income`() = runTest {
        val sources = attached().incomeSources
        assertEquals(listOf("Salary", "Freelance", "Interest"), sources.map { it.name })
        assertEquals(listOf(93, 6, 2), sources.map { it.sharePercent })
    }

    @Test fun `money deltas are current minus prior`() = runTest {
        val cashFlow = attached()
        assertEquals(Amount(1700L), cashFlow.moneyIn.magnitude)
        assertTrue(cashFlow.moneyIn.isPositive)
        assertEquals(Amount(190L), cashFlow.moneyOut.magnitude)
        assertTrue(cashFlow.moneyOut.isPositive)
    }

    @Test fun `savings rate change is points vs prior`() = runTest {
        val change = attached().savingsRateChange
        assertEquals(22, change.nowPercent)
        assertEquals(4, change.magnitudePoints) // prior rate 18 → +4 pts
        assertTrue(change.isPositive)
    }

    private suspend fun TestScope.attached(): CashFlowReportViewModel.Report {
        val viewModel = DefaultCashFlowReportViewModel(
            cashFlowReportUseCase = fakeUseCase,
            currencyPrimaryUseCase = fakeCurrency,
            onBackHandler = OnBackHandler.Noop,
            zonedClock = fakeClock,
            dispatchers = dispatchers,
        )
        viewModel.attach()
        runCurrent()
        return viewModel.state.first().report!!
    }

    private companion object {
        fun month(label: String, income: Long, expense: Long) = CashFlowReportUseCase.MonthlyCashFlow(label, Amount(income), Amount(expense))

        fun income(name: String, amount: Long) = CashFlowReportUseCase.IncomeSource(name, Image.empty(), ColorScheme.Grey, Amount(amount))

        fun sampleReport() = CashFlowReportUseCase.Report(
            totalIn = Amount(31100L),
            totalOut = Amount(24290L),
            months = listOf(
                month("Nov", 5050, 3980),
                month("Dec", 5600, 4820),
                month("Jan", 5050, 3640),
                month("Feb", 5050, 3910),
                month("Mar", 5300, 4180),
                month("Apr", 5050, 3760),
            ),
            incomeSources = listOf(
                income("Salary", 28800),
                income("Freelance", 1800),
                income("Interest", 500),
            ),
            priorTotalIn = Amount(29400L),
            priorTotalOut = Amount(24100L),
        )
    }
}
