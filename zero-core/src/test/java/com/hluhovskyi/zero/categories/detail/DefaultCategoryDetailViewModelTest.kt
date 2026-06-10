package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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

@RunWith(MockitoJUnitRunner::class)
class DefaultCategoryDetailViewModelTest {

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    @Mock private lateinit var categorySpendingUseCase: CategorySpendingUseCase

    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase

    @Mock private lateinit var amountFormatter: AmountFormatter

    @Mock private lateinit var onEditHandler: OnCategoryDetailEditHandler

    private val categoryId = Id.Known("cat1")
    private val primaryCurrency = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
    private val fixedInstant = Instant.parse("2026-05-02T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeZonedClock = object : ZonedClock {
        override fun now() = fixedInstant
        override fun timeZone() = testTimeZone
    }

    @Before
    fun setUp() {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(emptyFlow())
        whenever(categorySpendingUseCase.queryForCategory(any(), any())).thenReturn(flowOf(null))
        whenever(categorySpendingUseCase.queryMonthlyTrend(any(), any())).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `state reflects category name and color scheme from queryById`() = runTest {
        val blueScheme = ColorScheme(
            swatch = Color(id = Id("s"), value = ColorValue(0xFF1565C0UL)),
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
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals("Groceries", state.categoryName)
        assertEquals(blueScheme, state.categoryColorScheme)
    }

    @Test
    fun `state total and transactionCount come from queryForCategory`() = runTest {
        val spending = CategorySpendingUseCase.CategorySpending(
            categoryId = categoryId,
            totalAmount = Amount(BigDecimal("150.00")),
            transactionCount = 5,
        )
        whenever(categorySpendingUseCase.queryForCategory(any(), any())).thenReturn(flowOf(spending))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        assertEquals(0, BigDecimal("150.00").compareTo(state.totalAmount.value))
        assertEquals(5, state.transactionCount)
    }

    @Test
    fun `state averageAmount is totalAmount divided by transactionCount`() = runTest {
        val spending = CategorySpendingUseCase.CategorySpending(
            categoryId = categoryId,
            totalAmount = Amount(BigDecimal("100.00")),
            transactionCount = 4,
        )
        whenever(categorySpendingUseCase.queryForCategory(any(), any())).thenReturn(flowOf(spending))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        // 100 / 4 = 25.00
        assertEquals(0, BigDecimal("25.00").compareTo(vm.state.first().averageAmount.value))
    }

    @Test
    fun `state averageAmount is zero when transactionCount is zero`() = runTest {
        whenever(categorySpendingUseCase.queryForCategory(any(), any())).thenReturn(flowOf(null))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertEquals(Amount.zero(), vm.state.first().averageAmount)
    }

    @Test
    fun `state currencySymbol matches primary currency`() = runTest {
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        assertEquals("$", vm.state.first().currencySymbol)
    }

    @Test
    fun `periodDate is the first day of the current month`() = runTest {
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        // fixedInstant = 2026-05-02 → periodDate should be 2026-05-01
        assertEquals(LocalDate(2026, 5, 1), vm.state.first().periodDate)
    }

    @Test
    fun `state maps monthly trend with current month flagged`() = runTest {
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)
        whenever(amountFormatter.format(any(), any(), any()))
            .thenAnswer { "$" + (it.arguments[0] as Amount).value.toInt() }
        whenever(categorySpendingUseCase.queryMonthlyTrend(categoryId, 6)).thenReturn(
            flowOf(
                listOf(
                    CategorySpendingUseCase.MonthlySpending(LocalDate(2026, 3, 1), Amount(BigDecimal("280"))),
                    CategorySpendingUseCase.MonthlySpending(LocalDate(2026, 4, 1), Amount(BigDecimal("290"))),
                ),
            ),
        )

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val trend = vm.state.first().trend
        assertEquals(2, trend.size)
        assertEquals(false, trend.first().isCurrent)
        assertEquals(true, trend.last().isCurrent)
        assertEquals(290f, trend.last().value, 0.01f)
        assertEquals("$290", trend.last().amountLabel)
    }

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultCategoryDetailViewModel(
        categoryId = categoryId,
        categoriesQueryUseCase = categoriesQueryUseCase,
        categorySpendingUseCase = categorySpendingUseCase,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        amountFormatter = amountFormatter,
        onEditHandler = onEditHandler,
        onBackHandler = OnBackHandler.Noop,
        onCreateTransactionHandler = OnCategoryDetailCreateTransactionHandler.Noop,
        zonedClock = fakeZonedClock,
        coroutineScope = coroutineScope,
    )
}
