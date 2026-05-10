package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime

interface TransactionFilterApplicator {
    fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction>

    object Identity : TransactionFilterApplicator {
        override fun apply(
            transactions: List<TransactionRepository.Transaction>,
            filter: TransactionFilter,
        ) = transactions
    }
}

internal class DatePeriodTransactionFilterApplicator(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : TransactionFilterApplicator {
    override fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction> {
        val period = filter.period ?: return transactions
        val today = clock.localDateTime(zoneProvider.timeZone()).date
        val range = period.toDateRange(today)
        return transactions.filter { it.dateTime.date in range.start..range.end }
    }
}

internal object TypeTransactionFilterApplicator : TransactionFilterApplicator {
    override fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction> {
        if (filter.type == TransactionFilter.TransactionType.All) return transactions
        return transactions.filter { tx ->
            when (filter.type) {
                TransactionFilter.TransactionType.Expense -> tx is TransactionRepository.Transaction.Expense
                TransactionFilter.TransactionType.Income -> tx is TransactionRepository.Transaction.Income
                TransactionFilter.TransactionType.Transfer -> tx is TransactionRepository.Transaction.Transfer
                TransactionFilter.TransactionType.All -> true
            }
        }
    }
}

internal object CategoryTransactionFilterApplicator : TransactionFilterApplicator {
    override fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction> {
        val ids = filter.categoryIds ?: return transactions
        return transactions.filter { tx ->
            when (tx) {
                is TransactionRepository.Transaction.Expense -> tx.categoryId in ids
                is TransactionRepository.Transaction.Income -> tx.categoryId in ids
                is TransactionRepository.Transaction.Transfer -> false
            }
        }
    }
}

internal object AccountTransactionFilterApplicator : TransactionFilterApplicator {
    override fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction> {
        val ids = filter.accountIds ?: return transactions
        return transactions.filter { tx -> tx.accountId in ids }
    }
}

internal class DefaultTransactionFilterApplicator(
    clock: Clock,
    zoneProvider: ZoneProvider,
) : TransactionFilterApplicator {

    private val applicators = listOf(
        DatePeriodTransactionFilterApplicator(clock, zoneProvider),
        TypeTransactionFilterApplicator,
        CategoryTransactionFilterApplicator,
        AccountTransactionFilterApplicator,
    )

    override fun apply(
        transactions: List<TransactionRepository.Transaction>,
        filter: TransactionFilter,
    ): List<TransactionRepository.Transaction> {
        if (!filter.isActive) return transactions
        return applicators.fold(transactions) { acc, applicator -> applicator.apply(acc, filter) }
    }
}
