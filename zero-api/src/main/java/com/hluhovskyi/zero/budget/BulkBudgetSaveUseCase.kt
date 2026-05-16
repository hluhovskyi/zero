package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id

interface BulkBudgetSaveUseCase {

    suspend fun save(
        period: DateRange,
        type: BudgetType,
        entries: List<Entry>,
    )

    data class Entry(val categoryId: Id.Known, val amount: Amount)

    object Noop : BulkBudgetSaveUseCase {
        override suspend fun save(period: DateRange, type: BudgetType, entries: List<Entry>) = Unit
    }
}
