package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface BudgetQueryUseCase {

    fun query(from: LocalDate, to: LocalDate): Flow<List<Budgeted>>

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
    }
}
