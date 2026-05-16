package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal class DefaultBudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val periodResolver: PeriodResolver,
) : BudgetUseCase {

    override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<BudgetUseCase.View> =
        monthOffsetFlow.flatMapLatest { offset ->
            val today = periodResolver.today()
            val (currentStart, currentEnd) = periodResolver.monthOffsetFrom(today, offset)
            val (previousStart, previousEnd) = periodResolver.monthOffsetFrom(today, offset - 1)
            combine(
                budgetQueryUseCase.query(currentStart, currentEnd),
                budgetQueryUseCase.query(previousStart, previousEnd),
            ) { current, previous ->
                BudgetUseCase.View(
                    currentPeriodLabel = label(currentStart),
                    previousPeriodLabel = label(previousStart),
                    current = current,
                    previous = previous,
                )
            }
        }

    override suspend fun save(
        monthOffset: Int,
        type: BudgetType,
        categoryId: Id.Known,
        amount: Amount,
    ) {
        val (start, end) = periodFor(monthOffset)
        val existing = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, start, end, type))
            .firstOrNull()
        budgetRepository.insert(
            BudgetRepository.BudgetInsert(
                id = existing?.id ?: Id.Unknown,
                categoryId = categoryId,
                type = type,
                amount = amount,
                periodStart = start,
                periodEnd = end,
            ),
        )
    }

    override suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType) {
        val (currentStart, currentEnd) = periodFor(monthOffset)
        val (previousStart, previousEnd) = periodFor(monthOffset - 1)
        val previousBudgets = budgetRepository
            .query(BudgetRepository.Criteria.ForPeriod(previousStart, previousEnd, type))
            .firstOrNull().orEmpty()
        if (previousBudgets.isEmpty()) return
        val existing = budgetRepository
            .query(BudgetRepository.Criteria.ForPeriod(currentStart, currentEnd, type))
            .firstOrNull().orEmpty()
        val incomingCategoryIds = previousBudgets.map { it.categoryId }.toSet()
        existing
            .filter { it.categoryId !in incomingCategoryIds }
            .forEach { budgetRepository.delete(it.id) }
        budgetRepository.insert(
            previousBudgets.map { source ->
                val existingRow = existing.firstOrNull { it.categoryId == source.categoryId }
                BudgetRepository.BudgetInsert(
                    id = existingRow?.id ?: Id.Unknown,
                    categoryId = source.categoryId,
                    type = type,
                    amount = source.amount,
                    periodStart = currentStart,
                    periodEnd = currentEnd,
                )
            },
        )
    }

    private fun periodFor(monthOffset: Int): Pair<LocalDate, LocalDate> =
        periodResolver.monthOffsetFrom(periodResolver.today(), monthOffset)

    private fun label(date: LocalDate): String = "${monthName(date.month)} ${date.year}"

    private fun monthName(month: Month): String = month.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}
