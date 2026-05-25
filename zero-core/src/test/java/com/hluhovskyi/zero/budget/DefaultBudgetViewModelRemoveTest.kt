package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultBudgetViewModelRemoveTest {

    @Mock private lateinit var budgetUseCase: BudgetUseCase

    private val setCategory = Id.Known("cat-set")
    private val unsetCategory = Id.Known("cat-unset")

    private val currentStart = LocalDate(2026, 5, 1)
    private val currentEnd = LocalDate(2026, 5, 31)
    private val previousStart = LocalDate(2026, 4, 1)
    private val previousEnd = LocalDate(2026, 4, 30)

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    private fun row(categoryId: Id.Known, hasBudget: Boolean) = BudgetQueryUseCase.Budgeted(
        categoryId = categoryId,
        categoryName = "Cat ${categoryId.value}",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        spent = Amount(BigDecimal("10")),
        budgetId = if (hasBudget) Id.Known("budget-${categoryId.value}") else null,
        budgeted = Amount(if (hasBudget) BigDecimal("100") else BigDecimal.ZERO),
    )

    private fun state() = BudgetUseCase.State(
        currentPeriod = DateRange(currentStart, currentEnd),
        previousPeriod = DateRange(previousStart, previousEnd),
        current = listOf(row(setCategory, hasBudget = true), row(unsetCategory, hasBudget = false)),
        previous = emptyList(),
        summary = BudgetUseCase.Summary.empty,
        hasAnyBudget = true,
    )

    private fun viewModel(): DefaultBudgetViewModel {
        whenever(budgetUseCase.observe(any(), any())).thenReturn(flowOf(state()))
        return DefaultBudgetViewModel(
            budgetUseCase = budgetUseCase,
            onCategoryTappedHandler = OnCategoryTappedHandler.Noop,
            onOverActionTappedHandler = OnOverActionTappedHandler.Noop,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `TapRemove while editing a set budget opens the confirmation and closes the numpad`() = runTest {
        val vm = viewModel()
        vm.attach()

        vm.perform(BudgetViewModel.Action.TapCategory(setCategory))
        vm.perform(BudgetViewModel.Action.TapRemove)

        val state = vm.state.first()
        assertEquals(setCategory, state.removeConfirm)
        assertNull(state.editingCategoryId)
    }

    @Test
    fun `TapRemove while editing an unset category is a no-op`() = runTest {
        val vm = viewModel()
        vm.attach()

        vm.perform(BudgetViewModel.Action.TapCategory(unsetCategory))
        vm.perform(BudgetViewModel.Action.TapRemove)

        assertNull(vm.state.first().removeConfirm)
    }

    @Test
    fun `ConfirmRemove removes the budget for the current period and clears the confirmation`() = runTest {
        val vm = viewModel()
        vm.attach()

        vm.perform(BudgetViewModel.Action.TapCategory(setCategory))
        vm.perform(BudgetViewModel.Action.TapRemove)
        vm.perform(BudgetViewModel.Action.ConfirmRemove)

        verify(budgetUseCase).remove(0, BudgetType.EXPENSE, setCategory)
        assertNull(vm.state.first().removeConfirm)
    }

    @Test
    fun `CancelRemove clears the confirmation without removing`() = runTest {
        val vm = viewModel()
        vm.attach()

        vm.perform(BudgetViewModel.Action.TapCategory(setCategory))
        vm.perform(BudgetViewModel.Action.TapRemove)
        vm.perform(BudgetViewModel.Action.CancelRemove)

        verify(budgetUseCase, never()).remove(any(), any(), any())
        assertNull(vm.state.first().removeConfirm)
    }
}
