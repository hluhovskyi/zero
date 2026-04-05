package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.time.LocalDateTime

internal class DefaultTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val iconRepository: IconRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val onTransactionSelectedHandler: OnTransactionSelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : TransactionViewModel {

    private val mutableState = MutableStateFlow(TransactionViewModel.State())
    override val state: Flow<TransactionViewModel.State> = mutableState

    private val loadMoreTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun perform(action: TransactionViewModel.Action) {
        when (action) {
            is TransactionViewModel.Action.SelectTransaction -> {
                onTransactionSelectedHandler.onSelected(action.item.id)
            }

            is TransactionViewModel.Action.LoadMore -> {
                coroutineScope.launch {
                    loadMoreTrigger.emit(Unit)
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val initialTimestamp = LocalDateTime.now()
            combine(
                combine(
                    transactionRepository.query(TransactionRepository.Criteria.After(initialTimestamp))
                        .onStartWithEmptyList()
                        .onEmptyReturnEmptyList(),
                    transactionRepository.query(
                        TransactionRepository.Criteria.All(),
                        trigger = loadMoreTrigger
                    )
                        .onStartWithEmptyList()
                        .onEmptyReturnEmptyList(),
                ) { new, paged ->
                    val freshById = new.associateBy { it.id }
                    val merged = paged.map { transaction ->
                        val fresh = freshById[transaction.id]
                        if (fresh != null && fresh.updatedDateTime >= transaction.updatedDateTime) {
                            fresh
                        } else {
                            transaction
                        }
                    }
                    val existingIds = paged.map { it.id }.toSet()
                    val added = new.filter { it.id !in existingIds }
                    (added + merged).sortedByDescending { it.dateTime }
                },
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
                iconRepository.query(IconRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
            ) { transactions, idToCategories, idToAccounts, idToCurrencies, idToIcons ->
                val primaryCurrency = currencyPrimaryUseCase.getPrimaryCurrency()
                transactions
                    .mapNotNull { transaction ->
                        resolve(
                            transaction = transaction,
                            idToAccounts = idToAccounts,
                            idToCategories = idToCategories,
                            idToCurrencies = idToCurrencies,
                            idToIcons = idToIcons,
                        )
                    }
                    .groupBy { it.date.toLocalDate() }
                    .flatMap { (date, transactions) ->
                        val amount: Amount = transactions.fold(Amount.zero()) { amount, transaction ->
                            when (transaction) {
                                is TransactionViewModel.Item.Transaction.Expense -> {
                                    amount - if (transaction.conversion is TransactionViewModel.Conversion.WithAmount &&
                                        transaction.conversion.currencyId == primaryCurrency.id
                                    ) {
                                        transaction.conversion.amount
                                    } else {
                                        currencyConvertUseCase.convertToPrimary(
                                            transaction.amount,
                                            transaction.currencyId
                                        )
                                    }
                                }
                                is TransactionViewModel.Item.Transaction.Income -> {
                                    amount + if (transaction.conversion is TransactionViewModel.Conversion.WithAmount &&
                                        transaction.conversion.currencyId == primaryCurrency.id
                                    ) {
                                        transaction.conversion.amount
                                    } else {
                                        currencyConvertUseCase.convertToPrimary(
                                            transaction.amount,
                                            transaction.currencyId
                                        )
                                    }
                                }
                                is TransactionViewModel.Item.Transaction.Transfer -> amount - currencyConvertUseCase.convertToPrimary(
                                    transaction.amount,
                                    transaction.currencyId
                                ) + currencyConvertUseCase.convertToPrimary(
                                    transaction.targetAmount,
                                    transaction.targetCurrencyId
                                )
                            }
                        }

                        listOf(
                            TransactionViewModel.Item.Summary(
                                date = date,
                                total = amount,
                                currencySymbol = primaryCurrency.symbol,
                            )
                        ) + transactions
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
        idToIcons: Map<Id.Known, Icon>,
    ): TransactionViewModel.Item.Transaction? {
        return when (transaction) {
            is TransactionRepository.Transaction.Expense -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Expense(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    currencyId = transaction.currencyId,
                    conversion = if (transaction.currencyId != account.currencyId) {
                        val symbol = idToCurrencies[account.currencyId]?.symbol
                        TransactionViewModel.Conversion.WithAmount(
                            amount = transaction.amount.withRate(transaction.rate),
                            currencyId = account.currencyId,
                            currencySymbol = symbol.orEmpty()
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                    currencySymbol = currency.symbol,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    categoryName = category.name,
                    categoryColorScheme = category.colorScheme,
                    categoryIcon = category.icon,
                )
            }

            is TransactionRepository.Transaction.Income -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Income(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    currencySymbol = currency.symbol,
                    currencyId = transaction.currencyId,
                    categoryName = category.name,
                    categoryColorScheme = category.colorScheme,
                    categoryIcon = category.icon,
                    conversion = if (transaction.currencyId != account.currencyId) {
                        val symbol = idToCurrencies[account.currencyId]?.symbol
                        TransactionViewModel.Conversion.WithAmount(
                            amount = transaction.amount.withRate(transaction.rate),
                            currencyId = account.currencyId,
                            currencySymbol = symbol.orEmpty()
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                )
            }

            is TransactionRepository.Transaction.Transfer -> {
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null
                val targetAccount = idToAccounts[transaction.targetAccount] ?: return null
                val targetCurrency = idToCurrencies[targetAccount.currencyId] ?: return null

                TransactionViewModel.Item.Transaction.Transfer(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    accountName = account.name,
                    currencyId = transaction.currencyId,
                    currencySymbol = currency.symbol,
                    targetAccountName = targetAccount.name,
                    targetAmount = transaction.targetAmount,
                    targetCurrencyId = targetCurrency.id,
                    targetCurrencySymbol = targetCurrency.symbol,
                    transferIcon = idToIcons[IconRepository.transferIconId()]?.image ?: Image.empty(),
                    transferColorScheme = ColorScheme.Grey,
                )
            }
        }
    }
}