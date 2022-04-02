package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class DefaultAccountUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
) : AccountUseCase {

    override val accounts: Flow<List<Account>> = combine(
        accountRepository.query(AccountRepository.Criteria.All()),
        transactionRepository.query(TransactionRepository.Criteria.All())
            .map { transactions ->
                transactions.calculateBalance()
            }
            .onStart { emit(emptyMap()) }
    ) { accounts, accountIdToBalance ->
        accounts.map { account ->
            Account(
                id = account.id,
                name = account.name,
                balance = accountIdToBalance[account.id] ?: Amount.zero()
            )
        }
    }

    private fun List<TransactionRepository.Transaction>.calculateBalance(): Map<Id.Known, Amount> {
        val balances = mutableMapOf<Id.Known, Amount>().withDefault { Amount.zero() }

        forEach { transaction ->
            val accountId = transaction.accountId
            when (transaction) {
                is TransactionRepository.Transaction.Expense -> {
                    balances.compute(accountId) { balance ->
                        balance - transaction.amount
                    }
                }
                is TransactionRepository.Transaction.Income -> {
                    balances.compute(accountId) { balance ->
                        balance + transaction.amount
                    }
                }
                is TransactionRepository.Transaction.Transfer -> {
                    balances.compute(accountId) { balance ->
                        balance - transaction.amount
                    }
                    balances.compute(transaction.targetAccount) { balance ->
                        balance + transaction.targetAmount
                    }
                }
            }
        }

        return balances
    }
}

private inline fun MutableMap<Id.Known, Amount>.compute(id: Id.Known, block: (Amount) -> Amount) {
    this[id] = block(this.getValue(id))
}