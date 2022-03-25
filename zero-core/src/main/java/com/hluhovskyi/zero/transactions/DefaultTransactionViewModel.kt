package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : TransactionViewModel {

    private val mutableState = MutableStateFlow(TransactionViewModel.State())
    override val state: Flow<TransactionViewModel.State> = mutableState

    override fun perform(action: TransactionViewModel.Action) {
        when (action) {

        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                transactionRepository.query(TransactionRepository.Criteria.All())
                    .onStartWithEmptyList()
                    .onEmptyReturnEmptyList(),
                categoriesQueryUseCase.queryAll()
                    .onStartWithEmptyList()
                    .onEmptyReturnEmptyList()
                    .associateById(),
                accountRepository.query(AccountRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
                currencyRepository.query(CurrencyRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
            ) { transactions, idToCategories, idToAccounts, idToCurrencies ->
                transactions.mapNotNull { transaction ->
                    resolve(
                        transaction = transaction,
                        idToAccounts = idToAccounts,
                        idToCategories = idToCategories,
                        idToCurrencies = idToCurrencies,
                    )
                }
            }.collectLatest { items ->
                mutableState.update { state ->
                    state.copy(
                        transactions = items
                    )
                }
            }
        }
    }

    private fun resolve(
        transaction: TransactionRepository.Transaction,
        idToCategories: Map<Id.Known, CategoriesQueryUseCase.Category>,
        idToAccounts: Map<Id.Known, AccountRepository.Account>,
        idToCurrencies: Map<Id.Known, Currency>,
    ): TransactionViewModel.TransactionItem? {
        return when (transaction) {
            is TransactionRepository.Transaction.Expense -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null

                TransactionViewModel.TransactionItem.Expense(
                    id = transaction.id,
                    amount = transaction.amount,
                    conversion = if (transaction.currencyId != account.currencyId) {
                        val symbol = idToCurrencies[account.currencyId]?.symbol
                        TransactionViewModel.Conversion.WithAmount(
                            amount = transaction.amount.withRate(transaction.rate),
                            currencySymbol = symbol.orEmpty()
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                    currencySymbol = currency.symbol,
                    accountName = account.name,
                    categoryName = category.name,
                    categoryColor = category.color,
                    categoryIcon = category.icon,
                )
            }

            is TransactionRepository.Transaction.Income -> {
                val account = idToAccounts[transaction.accountId] ?: return null
                TransactionViewModel.TransactionItem.Income(
                    id = transaction.id,
                    amount = transaction.amount,
                    accountName = account.name
                )
            }

            is TransactionRepository.Transaction.Transfer -> {
                val account = idToAccounts[transaction.accountId] ?: return null
                val targetAccount = idToAccounts[transaction.targetAccount] ?: return null

                TransactionViewModel.TransactionItem.Transfer(
                    id = transaction.id,
                    amount = transaction.amount,
                    accountName = account.name,
                    targetAccountName = targetAccount.name
                )
            }
        }
    }
}