package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

interface BudgetQueryUseCase {

    fun query(from: LocalDate, to: LocalDate): Flow<List<Budgeted>>

    /**
     * Emits true when at least one expense category with a set budget is over budget for the
     * current month. Categories without a set budget never count.
     */
    fun observeAnyOver(): Flow<Boolean>

    data class Budgeted(
        val categoryId: Id.Known,
        val categoryName: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val spent: Amount,
        val budgetId: Id.Known?,
        val budgeted: Amount,
    )

    object Noop : BudgetQueryUseCase {
        override fun query(from: LocalDate, to: LocalDate): Flow<List<Budgeted>> = emptyFlow()
        override fun observeAnyOver(): Flow<Boolean> = flowOf(false)
    }
}
