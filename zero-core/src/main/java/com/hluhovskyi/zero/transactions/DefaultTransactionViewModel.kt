package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import java.io.Closeable

internal class DefaultTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val onTransactionSelectedHandler: OnTransactionSelectedHandler,
    private val filter: TransactionFilter = TransactionFilter.All,
    private val transactionFilterUseCase: TransactionFilterUseCase = TransactionFilterUseCase.Noop,
    private val transactionFilterApplicator: TransactionFilterApplicator,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
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
                if (mutableState.value.searchQuery.isBlank()) {
                    coroutineScope.launch {
                        loadMoreTrigger.emit(Unit)
                    }
                }
            }

            is TransactionViewModel.Action.UpdateSearchQuery -> {
                mutableState.update { it.copy(searchQuery = action.query) }
            }

            is TransactionViewModel.Action.DeleteTransaction -> {
                coroutineScope.launch {
                    transactionRepository.delete(action.id)
                }
            }

            is TransactionViewModel.Action.Filter.Open -> {
                transactionFilterUseCase.perform(
                    TransactionFilterUseCase.Action.Open(mutableState.value.activeFilter),
                )
            }

            is TransactionViewModel.Action.Filter.RemovePeriod -> {
                updateFilter { it.copy(period = null) }
            }

            is TransactionViewModel.Action.Filter.RemoveType -> {
                updateFilter { it.copy(type = TransactionFilter.TransactionType.All) }
            }

            is TransactionViewModel.Action.Filter.RemoveCategories -> {
                updateFilter { it.copy(categoryIds = null) }
            }

            is TransactionViewModel.Action.Filter.RemoveAccounts -> {
                updateFilter { it.copy(accountIds = null) }
            }

            is TransactionViewModel.Action.Filter.Clear -> {
                updateFilter { TransactionFilter() }
            }
        }
    }

    private fun updateFilter(transform: (TransactionFilter) -> TransactionFilter) {
        mutableState.update { state ->
            val newFilter = transform(state.activeFilter)
            transactionFilterUseCase.perform(TransactionFilterUseCase.Action.Apply(newFilter))
            state.copy(activeFilter = newFilter)
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            launch {
                transactionFilterUseCase.state.collect { useCaseState ->
                    if (useCaseState is TransactionFilterUseCase.State.Applied) {
                        mutableState.update { it.copy(activeFilter = useCaseState.filter) }
                    }
                }
            }

            val initialTimestamp = clock.localDateTime(zoneProvider.timeZone())

            // Use DB-level query for simple category or account filters (e.g. detail screens).
            // All other cases load everything and apply the filter in memory.
            val pagedTransactions: Flow<List<TransactionRepository.Transaction>> = when {
                filter.categoryIds != null &&
                    filter.period == null &&
                    filter.type == TransactionFilter.TransactionType.All &&
                    filter.accountIds == null ->
                    forCategoriesTransactionsFlow(filter.categoryIds)

                filter.accountIds != null &&
                    filter.period == null &&
                    filter.type == TransactionFilter.TransactionType.All &&
                    filter.categoryIds == null ->
                    forAccountsTransactionsFlow(filter.accountIds)

                else -> allTransactionsFlow(initialTimestamp)
            }

            // null = "no search active, use paged"; non-null = search results
            // Blank query emits null immediately (no debounce) so the combine doesn't stall.
            // Non-blank queries are debounced 300ms before hitting the DB.
            val searchTransactions = mutableState
                .map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(null)
                    } else {
                        flow<List<TransactionRepository.Transaction>?> {
                            delay(300L)
                            emitAll(transactionRepository.query(TransactionRepository.Criteria.Search(query)))
                        }
                    }
                }

            val rawTransactions = combine(pagedTransactions, searchTransactions) { paged, searchResult ->
                searchResult ?: paged
            }

            val activeFilterFlow = mutableState.map { it.activeFilter }.distinctUntilChanged()

            val filteredRawTransactions = combine(rawTransactions, activeFilterFlow) { transactions, activeFilter ->
                transactionFilterApplicator.apply(transactions, activeFilter)
            }

            val categoriesFlow = categoriesQueryUseCase.queryAll()
                .onStartWithEmptyList()
                .onEmptyReturnEmptyList()
                .associateById()

            val accountsFlow = accountRepository.query(AccountRepository.Criteria.All())
                .onEmptyReturnEmptyList()
                .associateById()

            combine(
                filteredRawTransactions,
                categoriesFlow,
                accountsFlow,
                currencyRepository.query(CurrencyRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
                iconRepository.query(IconRepository.Criteria.All())
                    .onEmptyReturnEmptyList()
                    .associateById(),
            ) { transactions, idToCategories, idToAccounts, idToCurrencies, idToIcons ->
                val primaryCurrency = currencyPrimaryUseCase.getPrimaryCurrency()

                val items = transactions
                    .mapNotNull { transaction ->
                        resolve(
                            transaction = transaction,
                            idToAccounts = idToAccounts,
                            idToCategories = idToCategories,
                            idToCurrencies = idToCurrencies,
                            idToIcons = idToIcons,
                        )
                    }
                    .groupBy { it.date.date }
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
                                            transaction.currencyId,
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
                                            transaction.currencyId,
                                        )
                                    }
                                }
                                is TransactionViewModel.Item.Transaction.Transfer -> amount - currencyConvertUseCase.convertToPrimary(
                                    transaction.amount,
                                    transaction.currencyId,
                                ) + currencyConvertUseCase.convertToPrimary(
                                    transaction.targetAmount,
                                    transaction.targetCurrencyId,
                                )
                            }
                        }

                        listOf(
                            TransactionViewModel.Item.Summary(
                                date = date,
                                total = amount,
                                currencySymbol = primaryCurrency.symbol,
                            ),
                        ) + transactions
                    }

                items
            }.collectLatest { items ->
                mutableState.update { state ->
                    state.copy(transactions = items)
                }
            }
        }
    }

    private fun allTransactionsFlow(
        initialTimestamp: LocalDateTime,
    ): Flow<List<TransactionRepository.Transaction>> = combine(
        transactionRepository.query(TransactionRepository.Criteria.After(initialTimestamp))
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList(),
        transactionRepository.query(
            TransactionRepository.Criteria.All(),
            trigger = loadMoreTrigger,
        )
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList(),
    ) { new, paged ->
        val freshById = new.associateBy { it.id }
        val merged = paged.map { transaction ->
            val fresh = freshById[transaction.id]
            if (fresh != null && fresh.updatedDateTime >= transaction.updatedDateTime) fresh else transaction
        }
        val existingIds = paged.map { it.id }.toSet()
        val added = new.filter { it.id !in existingIds }
        (added + merged).sortedByDescending { it.dateTime }
    }

    private fun forCategoriesTransactionsFlow(
        categoryIds: Set<Id.Known>,
    ): Flow<List<TransactionRepository.Transaction>> = transactionRepository.query(TransactionRepository.Criteria.ForCategories(categoryIds))
        .onStartWithEmptyList()
        .onEmptyReturnEmptyList()

    private fun forAccountsTransactionsFlow(
        accountIds: Set<Id.Known>,
    ): Flow<List<TransactionRepository.Transaction>> = transactionRepository.query(TransactionRepository.Criteria.ForAccounts(accountIds))
        .onStartWithEmptyList()
        .onEmptyReturnEmptyList()

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
                val accountColorScheme = (account.colorId as? Id.Known)
                    ?.let { colorRepository.schemeFor(it) } ?: ColorScheme.Grey

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
                            currencySymbol = symbol.orEmpty(),
                        )
                    } else {
                        TransactionViewModel.Conversion.None
                    },
                    currencySymbol = currency.symbol,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    accountColorScheme = accountColorScheme,
                    categoryName = category.name,
                    categoryColorScheme = category.colorScheme,
                    categoryIcon = category.icon,
                )
            }

            is TransactionRepository.Transaction.Income -> {
                val category = idToCategories[transaction.categoryId] ?: return null
                val account = idToAccounts[transaction.accountId] ?: return null
                val currency = idToCurrencies[transaction.currencyId] ?: return null
                val accountColorScheme = (account.colorId as? Id.Known)
                    ?.let { colorRepository.schemeFor(it) } ?: ColorScheme.Grey

                TransactionViewModel.Item.Transaction.Income(
                    id = transaction.id,
                    date = transaction.dateTime,
                    amount = transaction.amount,
                    accountName = account.name,
                    accountIcon = idToIcons[account.iconId]?.image ?: Image.empty(),
                    accountColorScheme = accountColorScheme,
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
                            currencySymbol = symbol.orEmpty(),
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
