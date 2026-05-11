package com.hluhovskyi.zero.accounts.detail

import com.hluhovskyi.zero.accounts.AccountDetailSpendingUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

internal class DefaultAccountDetailSpendingUseCase(
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : AccountDetailSpendingUseCase {

    override fun queryForAccount(
        accountId: Id.Known,
        period: AccountDetailSpendingUseCase.Period,
    ): Flow<AccountDetailSpendingUseCase.AccountSpending?> {
        val (from, to) = period.resolve()
        return transactionRepository
            .query(TransactionRepository.Criteria.ForAccountBetween(accountId, from, to))
            .flatMapLatest { transactions -> flow { emit(aggregate(transactions)) } }
    }

    private fun aggregate(
        transactions: List<TransactionRepository.Transaction>,
    ): AccountDetailSpendingUseCase.AccountSpending? {
        if (transactions.isEmpty()) return null
        var totalIn = Amount.zero()
        var totalOut = Amount.zero()
        var count = 0
        for (tx in transactions) {
            when (tx) {
                is TransactionRepository.Transaction.Income -> {
                    totalIn += tx.amount
                    count++
                }
                is TransactionRepository.Transaction.Expense -> {
                    totalOut += tx.amount
                    count++
                }
                is TransactionRepository.Transaction.Transfer -> Unit
            }
        }
        return AccountDetailSpendingUseCase.AccountSpending(
            totalIn = totalIn,
            totalOut = totalOut,
            transactionCount = count,
        )
    }

    private fun AccountDetailSpendingUseCase.Period.resolve(): Pair<LocalDate, LocalDate> {
        val today = clock.now().toLocalDateTime(zoneProvider.timeZone()).date
        return when (this) {
            is AccountDetailSpendingUseCase.Period.CurrentMonth ->
                LocalDate(today.year, today.month, 1) to today
        }
    }
}
