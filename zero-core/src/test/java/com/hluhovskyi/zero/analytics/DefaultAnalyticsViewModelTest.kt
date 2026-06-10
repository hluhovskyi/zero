package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAnalyticsViewModelTest {

    private val analytics = MutableStateFlow(emptyAnalytics())
    private val fakeUseCase = object : AnalyticsUseCase {
        override fun query(range: DateRange): Flow<AnalyticsUseCase.Analytics> = analytics
    }

    private var seeAllInvoked = false
    private var selectedCategory: Id.Known? = null

    private val fakeClock = object : Clock {
        override fun now() = Instant.parse("2026-04-15T12:00:00Z")
    }
    private val fakeZone = object : ZoneProvider {
        override fun timeZone() = TimeZone.UTC
    }
    private val fakeCurrency = object : CurrencyPrimaryUseCase {
        override suspend fun getPrimaryCurrency() = Currency(id = Id.Known("usd"), name = "US Dollar", symbol = "$")
        override suspend fun setPrimaryCurrency(id: Id.Known) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `cash flow exposes net, totals and per-month bars`() = runTest {
        analytics.value = analyticsWith(
            totalIn = "1500", totalOut = "1000",
            cashFlow = listOf(bucket("Jan", "1000", "400"), bucket("Feb", "500", "600")),
        )

        val cashFlow = attached().cashFlow!!

        assertEquals(0, BigDecimal("500").compareTo(cashFlow.net.value))
        assertEquals(
            listOf(
                AnalyticsViewModel.Bar("Jan", 1000f, 400f),
                AnalyticsViewModel.Bar("Feb", 500f, 600f),
            ),
            cashFlow.bars,
        )
    }

    @Test
    fun `donut keeps top 6 and folds the rest into Other, legend keeps top 3 plus Other`() = runTest {
        analytics.value = analyticsWith(breakdown = sevenCategories(), categoryCount = 9)

        val breakdown = attached().breakdown!!

        assertEquals(7, breakdown.donut.size) // 6 categories + Other
        assertNull(breakdown.donut.last().colorScheme) // Other slice
        assertEquals(100f, breakdown.donut.last().value)

        assertEquals(4, breakdown.legend.size) // 3 categories + Other
        assertEquals("Cat c1", breakdown.legend.first().name)
        assertNull(breakdown.legend.last().name) // Other
        assertEquals(4, breakdown.legend.last().sharePercent) // 100 / 2800 ≈ 4%
        assertEquals(9, breakdown.categoryCount)
        assertEquals(0, BigDecimal("2800").compareTo(breakdown.totalSpent.value))
    }

    @Test
    fun `donut total style is Whole under the threshold and Short above it`() = runTest {
        analytics.value = analyticsWith(breakdown = sevenCategories(), categoryCount = 9) // total 2,800
        assertEquals(AmountFormatter.Style.Whole, attached().breakdown!!.totalStyle)

        analytics.value = analyticsWith(
            breakdown = listOf(spend("big", amount = "150000", recent = "75000", prior = "75000")),
            categoryCount = 1,
        )
        assertEquals(AmountFormatter.Style.Short, attached().breakdown!!.totalStyle)
    }

    @Test
    fun `rows keep top 5 with share, bar fraction and trend`() = runTest {
        analytics.value = analyticsWith(breakdown = sevenCategories(), categoryCount = 9)

        val rows = attached().breakdown!!.rows

        assertEquals(5, rows.size)
        assertEquals(25, rows[0].sharePercent) // 700 / 2800
        assertEquals(0.25f, rows[0].barFraction)
        assertEquals(AnalyticsViewModel.Trend.Up(33), rows[0].trend) // 400 vs 300
        assertEquals(AnalyticsViewModel.Trend.Down(80), rows[1].trend) // 100 vs 500
        assertEquals(AnalyticsViewModel.Trend.Flat, rows[2].trend) // 250 vs 250
        assertEquals(AnalyticsViewModel.Trend.New, rows[3].trend) // prior 0
    }

    @Test
    fun `breakdown is null when there is no spend`() = runTest {
        analytics.value = analyticsWith(breakdown = emptyList(), categoryCount = 0)

        assertNull(attached().breakdown)
    }

    @Test
    fun `SeeAllCategories forwards to the handler`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.perform(AnalyticsViewModel.Action.SeeAllCategories)
        runCurrent()

        assertTrue(seeAllInvoked)
    }

    @Test
    fun `SelectCategory forwards the id to the handler`() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.perform(AnalyticsViewModel.Action.SelectCategory(Id.Known("c3")))
        runCurrent()

        assertEquals(Id.Known("c3"), selectedCategory)
    }

    private suspend fun TestScope.attached(): AnalyticsViewModel.State {
        val viewModel = createViewModel(backgroundScope)
        viewModel.attach()
        runCurrent()
        return viewModel.state.first()
    }

    private fun createViewModel(scope: CoroutineScope) = DefaultAnalyticsViewModel(
        analyticsUseCase = fakeUseCase,
        currencyPrimaryUseCase = fakeCurrency,
        onSeeAllCategoriesHandler = { seeAllInvoked = true },
        onAnalyticsCategorySelectedHandler = { selectedCategory = it },
        clock = fakeClock,
        zoneProvider = fakeZone,
        coroutineScope = scope,
    )

    private fun emptyAnalytics() = analyticsWith(breakdown = emptyList(), categoryCount = 0)

    private fun analyticsWith(
        totalIn: String = "0",
        totalOut: String = "0",
        cashFlow: List<AnalyticsUseCase.CashFlowBucket> = emptyList(),
        breakdown: List<SpendingBreakdownUseCase.CategorySpend> = emptyList(),
        categoryCount: Int = 0,
    ) = AnalyticsUseCase.Analytics(
        totalIn = Amount(BigDecimal(totalIn)),
        totalOut = Amount(BigDecimal(totalOut)),
        cashFlow = cashFlow,
        breakdown = SpendingBreakdownUseCase.Breakdown(
            total = breakdown.fold(Amount.zero()) { sum, row -> sum + row.amount },
            transactionCount = breakdown.sumOf { it.transactionCount },
            categoryCount = categoryCount,
            categories = breakdown,
        ),
    )

    private fun bucket(label: String, income: String, expense: String) = AnalyticsUseCase.CashFlowBucket(
        label = label,
        income = Amount(BigDecimal(income)),
        expense = Amount(BigDecimal(expense)),
    )

    // Seven categories (sorted desc), total 2800, with the four trend cases in the first four rows.
    private fun sevenCategories() = listOf(
        spend("c1", amount = "700", recent = "400", prior = "300"), // Up 33%
        spend("c2", amount = "600", recent = "100", prior = "500"), // Down 80%
        spend("c3", amount = "500", recent = "250", prior = "250"), // Flat
        spend("c4", amount = "400", recent = "400", prior = "0"), // New
        spend("c5", amount = "300", recent = "150", prior = "150"),
        spend("c6", amount = "200", recent = "100", prior = "100"),
        spend("c7", amount = "100", recent = "50", prior = "50"),
    )

    private fun spend(id: String, amount: String, recent: String, prior: String) = SpendingBreakdownUseCase.CategorySpend(
        categoryId = Id.Known(id),
        name = "Cat $id",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        amount = Amount(BigDecimal(amount)),
        transactionCount = 1,
        recentAmount = Amount(BigDecimal(recent)),
        priorAmount = Amount(BigDecimal(prior)),
    )
}
