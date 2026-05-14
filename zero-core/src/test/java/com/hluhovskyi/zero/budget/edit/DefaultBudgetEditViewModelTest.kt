package com.hluhovskyi.zero.budget.edit

import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultBudgetEditViewModelTest {

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase
    @Mock private lateinit var budgetRepository: BudgetRepository

    private val categoryId = Id.Known("cat-1")
    private val periodStart = LocalDate(2026, 5, 1)
    private val periodEnd = LocalDate(2026, 5, 31)

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    private fun category(name: String = "Groceries") = CategoriesQueryUseCase.Category(
        id = categoryId,
        name = name,
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
    )

    private fun budget(amount: BigDecimal) = BudgetRepository.Budget(
        id = Id.Known("budget-1"),
        categoryId = categoryId,
        type = BudgetType.EXPENSE,
        amount = Amount(amount),
        periodStart = periodStart,
        periodEnd = periodEnd,
    )

    private fun viewModel(
        onBudgetSavedHandler: OnBudgetSavedHandler = OnBudgetSavedHandler.Noop,
        onBackHandler: OnBackHandler = OnBackHandler.Noop,
    ) = DefaultBudgetEditViewModel(
        categoryId = categoryId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        categoriesQueryUseCase = categoriesQueryUseCase,
        budgetRepository = budgetRepository,
        onBudgetSavedHandler = onBudgetSavedHandler,
        onBackHandler = onBackHandler,
        dispatchers = dispatchers,
    )

    @Test
    fun `initial state when no existing budget - amountText is zero and isEditing is false`() = runTest {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category()))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria.ForCategoryAndPeriod>()))
            .thenReturn(flowOf(null))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        assertEquals("0", state.amountText)
        assertFalse(state.isEditing)
    }

    @Test
    fun `initial state when existing budget - amountText seeded and isEditing is true`() = runTest {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category()))
        val existingBudget = budget(BigDecimal("125.50"))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, periodStart, periodEnd)))
            .thenReturn(flowOf(existingBudget))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, periodStart.minus(1, DateTimeUnit.MONTH), periodEnd.minus(1, DateTimeUnit.MONTH))))
            .thenReturn(flowOf(null))

        val vm = viewModel()
        vm.attach()
        val state = vm.state.first()

        assertEquals("125.5", state.amountText)
        assertTrue(state.isEditing)
    }

    @Test
    fun `ChangeAmount updates amountText and clears isPreviousSelected`() = runTest {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category()))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria.ForCategoryAndPeriod>()))
            .thenReturn(flowOf(null))

        val vm = viewModel()
        vm.attach()
        vm.perform(BudgetEditViewModel.Action.ChangeAmount("125"))
        val state = vm.state.first()

        assertEquals("125", state.amountText)
        assertFalse(state.isPreviousSelected)
    }

    @Test
    fun `TapPreviousChip sets amountText to previous amount and isPreviousSelected to true`() = runTest {
        val prevAmount = BigDecimal("42")
        val prevBudget = BudgetRepository.Budget(
            id = Id.Known("prev-budget"),
            categoryId = categoryId,
            type = BudgetType.EXPENSE,
            amount = Amount(prevAmount),
            periodStart = periodStart.minus(1, DateTimeUnit.MONTH),
            periodEnd = periodEnd.minus(1, DateTimeUnit.MONTH),
        )
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category()))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, periodStart, periodEnd)))
            .thenReturn(flowOf(null))
        whenever(budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, periodStart.minus(1, DateTimeUnit.MONTH), periodEnd.minus(1, DateTimeUnit.MONTH))))
            .thenReturn(flowOf(prevBudget))

        val vm = viewModel()
        vm.attach()
        vm.perform(BudgetEditViewModel.Action.TapPreviousChip)
        val state = vm.state.first()

        assertEquals("42", state.amountText)
        assertTrue(state.isPreviousSelected)
    }

    @Test
    fun `TapSave inserts budget and invokes saved and back handlers`() = runTest {
        whenever(categoriesQueryUseCase.queryById(categoryId)).thenReturn(flowOf(category("Groceries")))
        whenever(budgetRepository.query(any<BudgetRepository.Criteria.ForCategoryAndPeriod>()))
            .thenReturn(flowOf(null))

        val savedHandler = mock<OnBudgetSavedHandler>()
        val backHandler = mock<OnBackHandler>()
        val vm = viewModel(onBudgetSavedHandler = savedHandler, onBackHandler = backHandler)
        vm.attach()
        vm.perform(BudgetEditViewModel.Action.ChangeAmount("100"))
        vm.perform(BudgetEditViewModel.Action.TapSave)

        val captor = argumentCaptor<BudgetRepository.BudgetInsert>()
        verify(budgetRepository).insert(captor.capture())
        val insert = captor.firstValue
        assertEquals(categoryId, insert.categoryId)
        assertEquals(BudgetType.EXPENSE, insert.type)
        assertEquals(0, insert.amount.value.compareTo(BigDecimal("100")))
        assertEquals(periodStart, insert.periodStart)
        assertEquals(periodEnd, insert.periodEnd)
        val amountCaptor = argumentCaptor<Amount>()
        verify(savedHandler).onSaved(eq("Groceries"), amountCaptor.capture())
        assertEquals(0, amountCaptor.firstValue.value.compareTo(BigDecimal("100")))
        verify(backHandler).onBack()
    }
}
