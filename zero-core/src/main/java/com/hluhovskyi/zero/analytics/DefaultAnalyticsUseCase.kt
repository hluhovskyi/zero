package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Owns all of the Analytics hub's aggregation. Reads every transaction in [range] (one query) plus
 * the category set, converts to the primary currency, then derives the monthly cash-flow buckets and
 * the per-category spend (with a recent-vs-prior split for the trend). Bucketing is monthly and works
 * for any range — the bar chart adapts to the bucket count.
 */
internal class DefaultAnalyticsUseCase(
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
) : AnalyticsUseCase {

    override fun query(range: DateRange): Flow<AnalyticsUseCase.Analytics> = combine(
        transactionRepository.query(
            TransactionRepository.Criteria.Filtered(
                from = range.start,
                to = range.end,
                type = null,
                categoryIds = null,
                accountIds = null,
            ),
        ),
        categoriesQueryUseCase.queryAll().onStartWithEmptyList(),
    ) { transactions, categories ->
        aggregate(range, transactions, categories)
    }

    private suspend fun aggregate(
        range: DateRange,
        transactions: List<TransactionRepository.Transaction>,
        categories: List<CategoriesQueryUseCase.Category>,
    ): AnalyticsUseCase.Analytics {
        val converted = transactions.map { transaction ->
            ConvertedTransaction(
                date = transaction.dateTime.date,
                amount = currencyConvertUseCase.convertToPrimary(transaction.amount, transaction.currencyId),
                expenseCategoryId = (transaction as? TransactionRepository.Transaction.Expense)?.categoryId,
                isIncome = transaction is TransactionRepository.Transaction.Income,
            )
        }
        val cashFlow = buildCashFlow(range, converted)
        return AnalyticsUseCase.Analytics(
            totalIn = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.income },
            totalOut = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.expense },
            cashFlow = cashFlow,
            breakdown = buildBreakdown(range, converted, categories),
            categoryCount = categories.count { it.type == CategoryType.EXPENSE },
        )
    }

    private fun buildCashFlow(
        range: DateRange,
        transactions: List<ConvertedTransaction>,
    ): List<AnalyticsUseCase.CashFlowBucket> {
        val months = monthStarts(range)
        val income = HashMap<Int, Amount>()
        val expense = HashMap<Int, Amount>()
        months.forEach { month ->
            income[monthKey(month)] = Amount.zero()
            expense[monthKey(month)] = Amount.zero()
        }
        transactions.forEach { transaction ->
            val key = monthKey(transaction.date)
            when {
                transaction.isIncome -> income[key]?.let { income[key] = it + transaction.amount }
                transaction.expenseCategoryId != null -> expense[key]?.let { expense[key] = it + transaction.amount }
            }
        }
        return months.map { month ->
            AnalyticsUseCase.CashFlowBucket(
                label = shortMonthLabel(month),
                income = income.getValue(monthKey(month)),
                expense = expense.getValue(monthKey(month)),
            )
        }
    }

    private fun buildBreakdown(
        range: DateRange,
        transactions: List<ConvertedTransaction>,
        categories: List<CategoriesQueryUseCase.Category>,
    ): List<AnalyticsUseCase.CategorySpend> {
        val midpoint = range.start.plus(range.start.daysUntil(range.end) / 2, DateTimeUnit.DAY)
        val categoriesById = categories.associateBy { it.id }
        val accumulators = LinkedHashMap<Id.Known, SpendAccumulator>()
        transactions.forEach { transaction ->
            val categoryId = transaction.expenseCategoryId ?: return@forEach
            if (categoryId !in categoriesById) return@forEach
            val accumulator = accumulators.getOrPut(categoryId) { SpendAccumulator() }
            accumulator.amount += transaction.amount
            accumulator.count += 1
            if (transaction.date >= midpoint) {
                accumulator.recent += transaction.amount
            } else {
                accumulator.prior += transaction.amount
            }
        }
        return accumulators
            .mapNotNull { (categoryId, accumulator) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                AnalyticsUseCase.CategorySpend(
                    categoryId = categoryId,
                    name = category.name,
                    icon = category.icon,
                    colorScheme = category.colorScheme,
                    amount = accumulator.amount,
                    transactionCount = accumulator.count,
                    recentAmount = accumulator.recent,
                    priorAmount = accumulator.prior,
                )
            }
            .sortedByDescending { it.amount.value }
    }

    private fun monthStarts(range: DateRange): List<LocalDate> = buildList {
        var month = LocalDate(range.start.year, range.start.monthNumber, 1)
        while (month <= range.end) {
            add(month)
            month = month.plus(1, DateTimeUnit.MONTH)
        }
    }

    private fun monthKey(date: LocalDate): Int = date.year * MONTHS_IN_YEAR + (date.monthNumber - 1)

    private fun shortMonthLabel(date: LocalDate): String = date.month.name.take(MONTH_LABEL_LENGTH).lowercase().replaceFirstChar { it.uppercase() }

    private class ConvertedTransaction(
        val date: LocalDate,
        val amount: Amount,
        val expenseCategoryId: Id.Known?,
        val isIncome: Boolean,
    )

    private class SpendAccumulator(
        var amount: Amount = Amount.zero(),
        var count: Int = 0,
        var recent: Amount = Amount.zero(),
        var prior: Amount = Amount.zero(),
    )

    private companion object {
        const val MONTHS_IN_YEAR = 12
        const val MONTH_LABEL_LENGTH = 3
    }
}
