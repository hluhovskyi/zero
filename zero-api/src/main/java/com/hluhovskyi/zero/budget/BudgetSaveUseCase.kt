package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id

interface BudgetSaveUseCase {

    suspend fun save(
        period: DateRange,
        type: BudgetType,
        categoryId: Id.Known,
        amount: Amount,
        existingId: Id.Known? = null,
    )

    object Noop : BudgetSaveUseCase {
        override suspend fun save(period: DateRange, type: BudgetType, categoryId: Id.Known, amount: Amount, existingId: Id.Known?) = Unit
    }
}
