package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest

internal class DefaultBudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val periodResolver: PeriodResolver,
) : BudgetUseCase {

    override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<BudgetUseCase.State> = monthOffsetFlow.flatMapLatest { offset ->
        val current = periodFor(offset)
        val previous = periodFor(offset - 1)
        combine(
            budgetQueryUseCase.query(current.start, current.end),
            budgetQueryUseCase.query(previous.start, previous.end),
        ) { currentBudgets, previousBudgets ->
            BudgetUseCase.State(
                currentPeriod = current,
                previousPeriod = previous,
                current = currentBudgets,
                previous = previousBudgets,
            )
        }
    }

    override suspend fun save(
        monthOffset: Int,
        type: BudgetType,
        categoryId: Id.Known,
        amount: Amount,
    ) {
        val period = periodFor(monthOffset)
        val existing = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, period.start, period.end, type))
            .firstOrNull()
        budgetRepository.insert(
            BudgetRepository.BudgetInsert(
                id = existing?.id ?: Id.Unknown,
                categoryId = categoryId,
                type = type,
                amount = amount,
                periodStart = period.start,
                periodEnd = period.end,
            ),
        )
    }

    override suspend fun replaceFromPrevious(monthOffset: Int, type: BudgetType) {
        val current = periodFor(monthOffset)
        val previous = periodFor(monthOffset - 1)
        val previousBudgets = budgetRepository
            .query(BudgetRepository.Criteria.ForPeriod(previous.start, previous.end, type))
            .firstOrNull().orEmpty()
        if (previousBudgets.isEmpty()) return
        val existing = budgetRepository
            .query(BudgetRepository.Criteria.ForPeriod(current.start, current.end, type))
            .firstOrNull().orEmpty()
        val inserts = previousBudgets.map { source ->
            val existingRow = existing.firstOrNull { it.categoryId == source.categoryId }
            BudgetRepository.BudgetInsert(
                id = existingRow?.id ?: Id.Unknown,
                categoryId = source.categoryId,
                type = type,
                amount = source.amount,
                periodStart = current.start,
                periodEnd = current.end,
            )
        }
        budgetRepository.replace(current, type, inserts)
    }

    private fun periodFor(monthOffset: Int): DateRange {
        val (start, end) = periodResolver.monthOffsetFrom(periodResolver.today(), monthOffset)
        return DateRange(start, end)
    }
}
