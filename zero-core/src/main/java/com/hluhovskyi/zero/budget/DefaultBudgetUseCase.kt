package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import java.math.BigDecimal

internal class DefaultBudgetUseCase(
    private val budgetRepository: BudgetRepository,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val periodResolver: PeriodResolver,
) : BudgetUseCase {

    override fun observe(monthOffsetFlow: Flow<Int>, type: BudgetType): Flow<BudgetUseCase.State> = monthOffsetFlow.flatMapLatest { offset ->
        val current = periodFor(offset)
        val previous = periodFor(offset - 1)
        // Trailing 3 calendar months ending the day before the current period, used only to
        // order the unset categories by how much the user actually spends on them.
        val trailingStart = current.start.minus(3, DateTimeUnit.MONTH)
        val trailingEnd = current.start.minus(1, DateTimeUnit.DAY)
        combine(
            budgetQueryUseCase.query(current.start, current.end),
            budgetQueryUseCase.query(previous.start, previous.end),
            budgetQueryUseCase.query(trailingStart, trailingEnd),
        ) { currentBudgets, previousBudgets, trailingBudgets ->
            val trailingSpend = trailingBudgets.associate { it.categoryId to it.spent }
            val sorted = sortByStatus(currentBudgets, trailingSpend)
            BudgetUseCase.State(
                currentPeriod = current,
                previousPeriod = previous,
                current = sorted,
                previous = previousBudgets,
                summary = summarize(sorted),
                hasAnyBudget = sorted.any { it.budgetId != null },
            )
        }
    }

    override fun observeAnyOver(type: BudgetType): Flow<Boolean> {
        val current = periodFor(0)
        return budgetQueryUseCase.query(current.start, current.end).map { rows ->
            rows.any { it.budgetId != null && it.spent > it.budgeted }
        }
    }

    private fun sortByStatus(
        rows: List<BudgetQueryUseCase.Budgeted>,
        trailingSpend: Map<Id.Known, Amount>,
    ): List<BudgetQueryUseCase.Budgeted> {
        val active = rows.filter { it.budgetId != null }
        val unset = rows.filter { it.budgetId == null }
        val over = active.filter { it.spent > it.budgeted }
        val inProgress = active.filter { it.spent <= it.budgeted }
            .sortedByDescending { row ->
                if (row.budgeted > Amount.zero()) row.spent / row.budgeted else 0.0
            }
        return over + inProgress + orderUnset(unset, trailingSpend)
    }

    /**
     * Categories the user actually spends on (positive trailing spend) come first, highest
     * average first; categories with no trailing history fall back to alphabetical order.
     */
    private fun orderUnset(
        unset: List<BudgetQueryUseCase.Budgeted>,
        trailingSpend: Map<Id.Known, Amount>,
    ): List<BudgetQueryUseCase.Budgeted> {
        fun spendOf(row: BudgetQueryUseCase.Budgeted): BigDecimal = trailingSpend[row.categoryId]?.value ?: BigDecimal.ZERO
        val (withHistory, withoutHistory) = unset.partition { spendOf(it) > BigDecimal.ZERO }
        return withHistory.sortedByDescending { spendOf(it) } +
            withoutHistory.sortedBy { it.categoryName }
    }

    private fun summarize(rows: List<BudgetQueryUseCase.Budgeted>): BudgetUseCase.Summary {
        val active = rows.filter { it.budgetId != null }
        val totalBudgeted = active.fold(Amount.zero()) { acc, b -> acc + b.budgeted }
        val totalSpent = active.fold(Amount.zero()) { acc, b -> acc + b.spent }
        val overCount = active.count { it.spent > it.budgeted }
        val pct = if (totalBudgeted > Amount.zero()) {
            (totalSpent.value.toDouble() / totalBudgeted.value.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        } else {
            0f
        }
        return BudgetUseCase.Summary(
            totalBudgeted = totalBudgeted,
            totalSpent = totalSpent,
            overCount = overCount,
            overallPct = pct,
            isOver = totalSpent > totalBudgeted,
        )
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

    override suspend fun remove(monthOffset: Int, type: BudgetType, categoryId: Id.Known) {
        val period = periodFor(monthOffset)
        val existing = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, period.start, period.end, type))
            .firstOrNull() ?: return
        budgetRepository.delete(existing.id)
    }

    private fun periodFor(monthOffset: Int): DateRange {
        val (start, end) = periodResolver.monthOffsetFrom(periodResolver.today(), monthOffset)
        return DateRange(start, end)
    }
}
