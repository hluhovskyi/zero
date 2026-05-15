package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate

interface BulkBudgetSaveUseCase {

    suspend fun save(
        from: LocalDate,
        to: LocalDate,
        type: BudgetType,
        entries: List<Entry>,
    )

    data class Entry(val categoryId: Id.Known, val amount: Amount)

    object Noop : BulkBudgetSaveUseCase {
        override suspend fun save(from: LocalDate, to: LocalDate, type: BudgetType, entries: List<Entry>) = Unit
    }
}
