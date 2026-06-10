package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Composes the Analytics hub model. Cash flow (monthly money in/out) is analytics-specific and
 * derived here; the category breakdown is delegated to the shared [SpendingBreakdownUseCase] so the
 * hub and the Spending report rank spend the same way. Monthly bucketing works for any range — the
 * bar chart adapts to the bucket count.
 */
internal class DefaultAnalyticsUseCase(
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val spendingBreakdownUseCase: SpendingBreakdownUseCase,
) : AnalyticsUseCase {

    override fun query(range: DateRange): Flow<AnalyticsUseCase.Analytics> {
        val criteria = TransactionRepository.Criteria.Filtered(
            from = range.start,
            to = range.end,
            type = null,
            categoryIds = null,
            accountIds = null,
        )
        val midpoint = range.start.plus(range.start.daysUntil(range.end) / 2, DateTimeUnit.DAY)
        return combine(
            transactionRepository.query(criteria),
            spendingBreakdownUseCase.query(criteria, trendSince = midpoint),
        ) { transactions, breakdown ->
            val cashFlow = buildCashFlow(range, transactions)
            AnalyticsUseCase.Analytics(
                totalIn = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.income },
                totalOut = cashFlow.fold(Amount.zero()) { sum, bucket -> sum + bucket.expense },
                cashFlow = cashFlow,
                breakdown = breakdown,
            )
        }
    }

    private suspend fun buildCashFlow(
        range: DateRange,
        transactions: List<TransactionRepository.Transaction>,
    ): List<AnalyticsUseCase.CashFlowBucket> {
        val months = monthStarts(range)
        val income = HashMap<Int, Amount>()
        val expense = HashMap<Int, Amount>()
        months.forEach { month ->
            income[monthKey(month)] = Amount.zero()
            expense[monthKey(month)] = Amount.zero()
        }
        transactions.forEach { transaction ->
            val key = monthKey(transaction.dateTime.date)
            when (transaction) {
                is TransactionRepository.Transaction.Income ->
                    income[key]?.let { income[key] = it + convert(transaction) }
                is TransactionRepository.Transaction.Expense ->
                    expense[key]?.let { expense[key] = it + convert(transaction) }
                is TransactionRepository.Transaction.Transfer -> Unit
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

    private suspend fun convert(transaction: TransactionRepository.Transaction): Amount = currencyConvertUseCase.convertToPrimary(transaction.amount, transaction.currencyId)

    private fun monthStarts(range: DateRange): List<LocalDate> = buildList {
        var month = LocalDate(range.start.year, range.start.monthNumber, 1)
        while (month <= range.end) {
            add(month)
            month = month.plus(1, DateTimeUnit.MONTH)
        }
    }

    private fun monthKey(date: LocalDate): Int = date.year * MONTHS_IN_YEAR + (date.monthNumber - 1)

    private fun shortMonthLabel(date: LocalDate): String = date.month.name.take(MONTH_LABEL_LENGTH).lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        const val MONTHS_IN_YEAR = 12
        const val MONTH_LABEL_LENGTH = 3
    }
}
