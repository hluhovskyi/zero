package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty

internal class DefaultAccountUseCase(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    currencyRepository: CurrencyRepository,
    iconRepository: IconRepository,
    currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    currencyConvertUseCase: CurrencyConvertUseCase,
) : AccountUseCase {

    override val state: Flow<AccountUseCase.State> = combine(
        accountRepository.query(AccountRepository.Criteria.All()),
        transactionRepository.query(TransactionRepository.Criteria.All())
            .map { transactions ->
                transactions.calculateBalance()
            }
            .onEmpty { emit(emptyMap()) },
        currencyRepository.query(CurrencyRepository.Criteria.All())
            .onEmptyReturnEmptyList()
            .associateById(),
        iconRepository.query(IconRepository.Criteria.All())
            .onEmptyReturnEmptyList()
            .associateById(),
    ) { accounts, accountIdToBalance, idToCurrency, idToIcon ->
        val resultAccounts = accounts.map { account ->
            Account(
                id = account.id,
                name = account.name,
                balance = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
                currencySymbol = idToCurrency[account.currencyId]?.symbol.orEmpty(),
                icon = idToIcon[account.iconId]?.image ?: Image.empty(),
            )
        }
        val balance = accounts.fold(Amount.zero()) { total, account ->
            total + currencyConvertUseCase.convertToPrimary(
                amount = account.initialBalance + (accountIdToBalance[account.id] ?: Amount.zero()),
                currencyId = account.currencyId
            )
        }

        AccountUseCase.State(
            balance = balance,
            currency = currencyPrimaryUseCase.getPrimaryCurrency(),
            accounts = resultAccounts
        )
    }

    override fun perform(action: AccountUseCase.Action) {

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