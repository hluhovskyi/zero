package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id

internal class DefaultBudgetSaveUseCase(
    private val budgetRepository: BudgetRepository,
) : BudgetSaveUseCase {

    override suspend fun save(
        period: DateRange,
        type: BudgetType,
        categoryId: Id.Known,
        amount: Amount,
        existingId: Id.Known?,
    ) {
        budgetRepository.insert(
            BudgetRepository.BudgetInsert(
                id = existingId ?: Id.Unknown,
                categoryId = categoryId,
                type = type,
                amount = amount,
                periodStart = period.start,
                periodEnd = period.end,
            ),
        )
    }
}
