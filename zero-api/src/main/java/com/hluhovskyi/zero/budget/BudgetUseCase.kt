package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
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
     * Observe the budget view for [monthOffsetFlow] (0 = current month, -1 = previous, etc.).
     * Re-emits whenever the offset changes or the underlying budgets/categories/spending change.
     */
    fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType = BudgetType.EXPENSE): Flow<View>

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

    data class View(
        val currentPeriodLabel: String,
        val previousPeriodLabel: String,
        val current: List<BudgetQueryUseCase.Budgeted>,
        val previous: List<BudgetQueryUseCase.Budgeted>,
    )

    object Noop : BudgetUseCase {
        override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<View> = emptyFlow()
        override suspend fun save(monthOffset: Int, type: BudgetType, categoryId: Id.Known, amount: Amount) = Unit
        override suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType) = Unit
    }
}
