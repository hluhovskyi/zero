package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.Transaction
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

private const val TAG = "DefaultTransactionEditViewModel"

internal class DefaultTransactionEditViewModel(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    private val idGenerator: IdGenerator,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    logger: Logger,
): TransactionEditViewModel {

    private val logger = logger.withTag(TAG)

    private val mutableState = MutableStateFlow(TransactionEditViewModel.State())
    override val state: Flow<TransactionEditViewModel.State> = mutableState

    override fun perform(action: TransactionEditViewModel.Action) {
        logger.d("perform=$action")
        when (action) {
            is TransactionEditViewModel.Action.ChangeAmount -> {
                mutableState.update { state ->
                    state.copy(amount = action.amount)
                }
            }
            is TransactionEditViewModel.Action.ChangeRate -> {
                mutableState.update { state ->
                    state.copy(rate = action.rate)
                }
            }
            is TransactionEditViewModel.Action.SelectAccount -> {
                mutableState.update { state ->
                    state.copy(selectedAccount = action.account)
                }
            }
            is TransactionEditViewModel.Action.SelectCurrency -> {
                mutableState.update { state ->
                    state.copy(selectedCurrency = action.currency)
                }
            }
            is TransactionEditViewModel.Action.EditCategories -> {
                coroutineScope.launch(context = Dispatchers.Main) {
                    onEditCategoriesHandler.onEdit()
                }
            }
            is TransactionEditViewModel.Action.Save -> {
                coroutineScope.launch(context = Dispatchers.IO) {
                    val state = mutableState.value
                    val account = state.selectedAccount ?: return@launch
                    val currency = state.selectedCurrency ?: return@launch
                    transactionRepository.insert(
                        Transaction.Expense(
                            id = idGenerator(),
                            amount = Amount(state.amount.toBigDecimalOrNull()),
                            accountId = account.id,
                            currencyId = currency.id,
                            rate = if (account.currencyId == currency.id) {
                                Rate.Same
                            } else {
                                Rate(state.rate.toBigDecimalOrNull())
                            }
                        )
                    )
                    launch(context = Dispatchers.Main) {
                        onTransactionSavedHandler.onSaved()
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            accountRepository.query(AccountRepository.Criteria.All())
                .collectLatest { accounts ->
                    mutableState.update { state ->
                        state.copy(
                            accounts = accounts,
                            selectedAccount = state.selectedAccount ?: accounts.firstOrNull()
                        )
                    }
                }

            categoryRepository.query(CategoryRepository.Criteria.All())
                .collectLatest { categories ->
                    mutableState.update { state ->
                        state.copy(
                            categories = categories,
                            selectedCategory = state.selectedCategory ?: categories.firstOrNull()
                        )
                    }
                }

            currencyRepository.query(CurrencyRepository.Criteria.All())
                .collectLatest { currencies ->
                    mutableState.update { state ->
                        state.copy(
                            currencies = currencies,
                            selectedCurrency = state.selectedCurrency ?: currencies.firstOrNull()
                        )
                    }
                }
        }
    }
}