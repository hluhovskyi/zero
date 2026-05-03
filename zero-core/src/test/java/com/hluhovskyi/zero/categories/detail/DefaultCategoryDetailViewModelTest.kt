package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategorySpendingUseCase
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultCategoryDetailViewModelTest {

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    @Mock private lateinit var categorySpendingUseCase: CategorySpendingUseCase

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var currencyConvertUseCase: CurrencyConvertUseCase

    @Mock private lateinit var currencyPrimaryUseCase: CurrencyPrimaryUseCase

    @Mock private lateinit var onEditHandler: OnCategoryDetailEditHandler

    @Mock private lateinit var onBackHandler: OnCategoryDetailBackHandler

    private val categoryId = Id.Known("cat1")
    private val primaryCurrency = Currency(id = Id.Known("c1"), name = "US Dollar", symbol = "$")
    private val fixedInstant = Instant.parse("2026-05-02T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
    }
    private val fakeZoneProvider = object : ZoneProvider {
        override fun timeZone() = testTimeZone
    }

    @Before
    fun setUp() {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(emptyFlow())
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.ForCategory>(), any()))
            .thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `state reflects category name and color scheme from queryById`() = runTest {
        val blueScheme = ColorScheme(
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
    fun `state total and transactionCount come from CategorySpendingUseCase for this category`() = runTest {
        val spending = CategorySpendingUseCase.CategorySpending(
            categoryId = categoryId,
            totalAmount = Amount(BigDecimal("150.00")),
            transactionCount = 5,
        )
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(listOf(spending)))
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
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(listOf(spending)))
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        val state = vm.state.first()
        // 100 / 4 = 25.00
        assertEquals(0, BigDecimal("25.00").compareTo(state.averageAmount.value))
    }

    @Test
    fun `state averageAmount is zero when transactionCount is zero`() = runTest {
        whenever(categorySpendingUseCase.query(any())).thenReturn(flowOf(emptyList()))
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
    fun `periodLabel is formatted as month name and year`() = runTest {
        whenever(currencyPrimaryUseCase.getPrimaryCurrency()).thenReturn(primaryCurrency)

        val vm = createViewModel(backgroundScope)
        vm.attach()
        runCurrent()

        // fixedInstant = 2026-05-02 → "May 2026"
        assertEquals("May 2026", vm.state.first().periodLabel)
    }

    private fun createViewModel(coroutineScope: CoroutineScope) = DefaultCategoryDetailViewModel(
        categoryId = categoryId,
        categoriesQueryUseCase = categoriesQueryUseCase,
        categorySpendingUseCase = categorySpendingUseCase,
        transactionRepository = transactionRepository,
        currencyConvertUseCase = currencyConvertUseCase,
        currencyPrimaryUseCase = currencyPrimaryUseCase,
        onEditHandler = onEditHandler,
        onBackHandler = onBackHandler,
        clock = fakeClock,
        zoneProvider = fakeZoneProvider,
        coroutineScope = coroutineScope,
    )
}
