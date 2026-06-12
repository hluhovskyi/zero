package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Buckets every transaction in [range] by calendar month into income/expense totals, converting
 * each to the primary currency. Empty months are emitted as zero buckets so the series is dense.
 */
internal class DefaultMonthlyCashFlowUseCase(
    private val transactionRepository: TransactionRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
) : MonthlyCashFlowUseCase {

    override fun query(range: DateRange): Flow<List<MonthlyCashFlowUseCase.MonthBucket>> {
        val filter = TransactionFilterCriteria(from = range.start, to = range.end)
        val criteria = TransactionRepository.Criteria.Filtered(filter = filter, type = null)
        return transactionRepository.query(criteria).map { transactions -> buildBuckets(range, transactions) }
    }

    private suspend fun buildBuckets(
        range: DateRange,
        transactions: List<TransactionRepository.Transaction>,
    ): List<MonthlyCashFlowUseCase.MonthBucket> {
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
            MonthlyCashFlowUseCase.MonthBucket(
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
