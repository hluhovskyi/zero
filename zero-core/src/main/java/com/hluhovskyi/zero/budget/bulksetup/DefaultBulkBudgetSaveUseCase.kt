package com.hluhovskyi.zero.budget.bulksetup

import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.budget.BulkBudgetSaveUseCase
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

internal class DefaultBulkBudgetSaveUseCase(
    private val budgetRepository: BudgetRepository,
) : BulkBudgetSaveUseCase {

    override suspend fun save(
        from: LocalDate,
        to: LocalDate,
        type: BudgetType,
        entries: List<BulkBudgetSaveUseCase.Entry>,
    ) {
        val existing = budgetRepository.query(BudgetRepository.Criteria.ForPeriod(from, to, type))
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
                    periodStart = from,
                    periodEnd = to,
                )
            },
        )
    }
}
