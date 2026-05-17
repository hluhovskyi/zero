package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBudgetViewModelSortTest {

    private val periodStart = LocalDate(2026, 5, 1)
    private val periodEnd = LocalDate(2026, 5, 31)

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun main() = dispatcher
        override fun io() = dispatcher
        override fun cpu() = dispatcher
    }

    @Test
    fun `sort puts over-budget first, then in-progress by pct desc, then unset last`() = runTest {
        // Inputs in arbitrary order (matches repo emission order).
        val rows = listOf(
            row("in-30", budgetId = "b1", budgeted = "100", spent = "30"),    // pct 0.30
            row("over-1", budgetId = "b2", budgeted = "100", spent = "150"),  // over
            row("unset-a", budgetId = null, budgeted = "0", spent = "5"),
            row("in-80", budgetId = "b3", budgeted = "100", spent = "80"),    // pct 0.80
            row("over-2", budgetId = "b4", budgeted = "50", spent = "60"),    // over
            row("unset-b", budgetId = null, budgeted = "0", spent = "0"),
            row("in-50", budgetId = "b5", budgeted = "100", spent = "50"),    // pct 0.50
            row("over-3", budgetId = "b6", budgeted = "10", spent = "15"),    // over
        )

        val viewModel = DefaultBudgetViewModel(
            budgetUseCase = ScriptedUseCase(rows),
            onCategoryTappedHandler = OnCategoryTappedHandler.Noop,
            dispatchers = dispatchers,
        )
        viewModel.attach()

        val state = viewModel.state.first { !it.isLoading }
        val ids = state.budgeted.map { it.categoryId.value }

        // 1) over-budget block: relative input order preserved.
        // 2) in-progress block: 0.80, 0.50, 0.30.
        // 3) unset block: relative input order preserved.
        assertEquals(
            listOf("over-1", "over-2", "over-3", "in-80", "in-50", "in-30", "unset-a", "unset-b"),
            ids,
        )
    }

    private fun row(
        categoryId: String,
        budgetId: String?,
        budgeted: String,
        spent: String,
    ) = BudgetQueryUseCase.Budgeted(
        categoryId = Id.Known(categoryId),
        categoryName = "Cat $categoryId",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        spent = Amount(BigDecimal(spent)),
        budgetId = budgetId?.let { Id.Known(it) },
        budgeted = Amount(BigDecimal(budgeted)),
    )

    private inner class ScriptedUseCase(
        private val current: List<BudgetQueryUseCase.Budgeted>,
    ) : BudgetUseCase {
        override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<BudgetUseCase.State> = flowOf(
            BudgetUseCase.State(
                currentPeriod = DateRange(periodStart, periodEnd),
                previousPeriod = DateRange(periodStart, periodEnd),
                current = current,
                previous = emptyList(),
            ),
        )

        override suspend fun save(monthOffset: Int, type: BudgetType, categoryId: Id.Known, amount: Amount) = Unit
        override suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType) = Unit
    }
}
