package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.firstOrNull

internal class DefaultBulkBudgetSaveUseCase(
    private val budgetRepository: BudgetRepository,
) : BulkBudgetSaveUseCase {

    override suspend fun save(
        period: DateRange,
        type: BudgetType,
        entries: List<BulkBudgetSaveUseCase.Entry>,
    ) {
        val existing = budgetRepository.query(BudgetRepository.Criteria.ForPeriod(period.start, period.end, type))
            .firstOrNull().orEmpty()
        val newCategoryIds = entries.map { it.categoryId }.toSet()
        existing
            .filter { it.categoryId !in newCategoryIds }
            .forEach { budgetRepository.delete(it.id) }
        budgetRepository.insert(
            entries.map { entry ->
                val existingRow = existing.firstOrNull { it.categoryId == entry.categoryId }
                BudgetRepository.BudgetInsert(
                    id = existingRow?.id ?: Id.Unknown,
                    categoryId = entry.categoryId,
                    type = type,
                    amount = entry.amount,
                    periodStart = period.start,
                    periodEnd = period.end,
                )
            },
        )
    }
}
