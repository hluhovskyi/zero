package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Domain entry point for budget operations. The VM is a thin layer between UI events and this
 * interface; period math, query observation, existing-row lookups, period copies, and (over
 * time) conflict resolution / reallocation all live here so the VM stays focused on UI state.
 */
interface BudgetUseCase {

    /**
     * Observe the budget state for [monthOffsetFlow] (0 = current month, -1 = previous, etc.).
     * Re-emits whenever the offset changes or the underlying budgets/categories/spending change.
     */
    fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType = BudgetType.EXPENSE): Flow<State>

    /** Upsert a single budget for [categoryId] in the period derived from [monthOffset]. */
    suspend fun save(
        monthOffset: Int,
        type: BudgetType,
        categoryId: Id.Known,
        amount: Amount,
    )

    /**
     * Atomic copy of the previous period's budgets into the period derived from [monthOffset].
     * Any current budget whose category isn't present in the source is deleted.
     */
    suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType)

    /**
     * Soft-delete the budget for [categoryId] in the period derived from [monthOffset]. Scoped to
     * that one period — budgets for the same category in other periods are untouched. No-op when
     * the category has no budget in the period.
     */
    suspend fun remove(monthOffset: Int, type: BudgetType, categoryId: Id.Known)

    data class State(
        val currentPeriod: DateRange,
        val previousPeriod: DateRange,
        val current: List<BudgetQueryUseCase.Budgeted>,
        val previous: List<BudgetQueryUseCase.Budgeted>,
        val summary: Summary,
        val hasAnyBudget: Boolean,
    )

    /**
     * Aggregate totals for the active (budget-set) rows in the current period.
     * Derived once in the use case so the view layer is a pure consumer.
     */
    data class Summary(
        val totalBudgeted: Amount,
        val totalSpent: Amount,
        val overCount: Int,
        val overallPct: Float,
        val isOver: Boolean,
    ) {
        companion object {

            val empty: Summary = Summary(
                totalBudgeted = Amount.zero(),
                totalSpent = Amount.zero(),
                overCount = 0,
                overallPct = 0f,
                isOver = false,
            )
        }
    }

    object Noop : BudgetUseCase {
        override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<State> = emptyFlow()
        override suspend fun save(monthOffset: Int, type: BudgetType, categoryId: Id.Known, amount: Amount) = Unit
        override suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType) = Unit
        override suspend fun remove(monthOffset: Int, type: BudgetType, categoryId: Id.Known) = Unit
    }
}
