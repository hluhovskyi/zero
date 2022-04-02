package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

private const val TAG = "DefaultTransactionEditUseCase"

internal class DefaultTransactionEditUseCase(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val idGenerator: IdGenerator,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val clock: Clock,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    logger: Logger,
) : TransactionEditUseCase {

    private val logger = logger.withTag(TAG)

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<TransactionEditUseCase.State> = mutableState
        .map { state ->
            when (state.transactionType) {
                TransactionEditType.EXPENSE -> TransactionEditUseCase.State.Expense(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.amount
                )
                TransactionEditType.INCOME -> TransactionEditUseCase.State.Income(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.amount
                )
                TransactionEditType.TRANSFER -> TransactionEditUseCase.State.Transfer(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    targetAccounts = state.targetAccounts,
                    selectedTargetAccount = state.selectedTargetAccount,
                    amount = state.amount,
                )
            }
        }

    override fun perform(action: TransactionEditUseCase.Action) {
        logger.d("perform=$action")
        when (action) {
            is TransactionEditUseCase.Action.ChangeAmount -> {
                mutableState.update { state ->
                    state.copy(amount = action.amount)
                }
            }
            is TransactionEditUseCase.Action.ChangeRate -> {
                mutableState.update { state ->
                    state.copy(rate = action.rate)
                }
            }
            is TransactionEditUseCase.Action.SelectAccount -> {
                mutableState.update { state ->
                    state.copy(selectedAccount = action.account)
                }
            }
            is TransactionEditUseCase.Action.SelectCategory -> {
                mutableState.update { state ->
                    state.copy(selectedCategory = action.category)
                }
            }
            is TransactionEditUseCase.Action.SelectCurrency -> {
                mutableState.update { state ->
                    state.copy(selectedCurrency = action.currency)
                }
            }
            is TransactionEditUseCase.Action.SelectTargetAccount -> {
                mutableState.update { state ->
                    state.copy(selectedTargetAccount = action.account)
                }
            }
            is TransactionEditUseCase.Action.SwitchTransaction -> {
                mutableState.update { state ->
                    state.copy(transactionType = action.type)
                }
            }
            is TransactionEditUseCase.Action.EditCategories -> {
                coroutineScope.launch(context = Dispatchers.Main) {
                    onEditCategoriesHandler.onEdit()
                }
            }
            is TransactionEditUseCase.Action.Save -> {
                coroutineScope.launch(context = Dispatchers.IO) {
                    val state = mutableState.value
                    val transaction = when (state.transactionType) {
                        TransactionEditType.EXPENSE -> {
                            val account = state.selectedAccount ?: return@launch
                            val currency = state.selectedCurrency ?: return@launch
                            val category = state.selectedCategory ?: return@launch

                            TransactionRepository.Transaction.Expense(
                                id = idGenerator(),
                                amount = Amount(state.amount.toBigDecimalOrNull()),
                                accountId = account.id,
                                currencyId = currency.id,
                                categoryId = category.id,
                                dateTime = clock.localDateTime(),
                                rate = Rate(state.amount.toBigDecimalOrNull())
                            )
                        }
                        TransactionEditType.INCOME -> {
                            val account = state.selectedAccount ?: return@launch
                            val currency = state.selectedCurrency ?: return@launch
                            val category = state.selectedCategory ?: return@launch

                            TransactionRepository.Transaction.Income(
                                id = idGenerator(),
                                amount = Amount(state.amount.toBigDecimalOrNull()),
                                accountId = account.id,
                                currencyId = currency.id,
                                categoryId = category.id,
                                dateTime = clock.localDateTime(),
                                rate = Rate(state.amount.toBigDecimalOrNull())
                            )
                        }
                        TransactionEditType.TRANSFER -> {
                            val account = state.selectedAccount ?: return@launch
                            val targetAccount = state.selectedTargetAccount ?: return@launch
                            val currency = state.selectedCurrency ?: return@launch

                            TransactionRepository.Transaction.Transfer(
                                id = idGenerator(),
                                amount = Amount(state.amount.toBigDecimalOrNull()),
                                accountId = account.id,
                                currencyId = currency.id,
                                targetAccount = targetAccount.id,
                                dateTime = clock.localDateTime(),
                                targetAmount = Amount(state.amount.toBigDecimalOrNull())
                            )
                        }
                    }

                    transactionRepository.insert(transaction)
                    launch(context = Dispatchers.Main) {
                        onTransactionSavedHandler.onSaved()
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            launch {
                accountRepository.query(AccountRepository.Criteria.All())
                    .map { accounts ->
                        accounts.map { account ->
                            TransactionEditAccount(
                                id = account.id,
                                name = account.name,
                                currencyId = account.currencyId
                            )
                        }
                    }
                    .collectLatest { accounts ->
                        logger.d("attach, accounts=$accounts")
                        mutableState.update { state ->
                            state.copy(
                                accounts = accounts,
                                selectedAccount = state.selectedAccount ?: accounts.firstOrNull(),
                                targetAccounts = accounts,
                                selectedTargetAccount = state.selectedTargetAccount ?: accounts.firstOrNull()
                            )
                        }
                    }
            }

            launch {
                categoriesQueryUseCase.queryAll()
                    .map { categories ->
                        categories.map { category ->
                            TransactionEditCategory(
                                id = category.id,
                                name = category.name,
                                color = category.color,
                                icon = category.icon,
                            )
                        }
                    }
                    .collectLatest { categories ->
                        logger.d("attach, categories=$categories")
                        mutableState.update { state ->
                            state.copy(
                                categories = categories,
                                selectedCategory = if (state.selectedCategory != null) {
                                    val updated = categories.find { it.id == state.selectedCategory.id }
                                    if (updated != state.selectedCategory) {
                                        updated
                                    } else {
                                        state.selectedCategory
                                    }
                                } else {
                                    state.selectedCategory ?: categories.firstOrNull()
                                }
                            )
                        }
                    }
            }

            launch {
                currencyRepository.query(CurrencyRepository.Criteria.All())
                    .map { currencies ->
                        currencies.map { currency ->
                            TransactionEditCurrency(
                                id = currency.id,
                                name = currency.name,
                                currencySymbol = currency.symbol
                            )
                        }
                    }
                    .collectLatest { currencies ->
                        logger.d("attach, currencies=$currencies")
                        mutableState.update { state ->
                            state.copy(
                                currencies = currencies,
                                selectedCurrency = state.selectedCurrency
                                    ?: currencies.firstOrNull()
                            )
                        }
                    }
            }
        }
    }

    private data class CompositeState(
        val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val categories: List<TransactionEditCategory> = emptyList(),
        val selectedCategory: TransactionEditCategory? = null,
        val currencies: List<TransactionEditCurrency> = emptyList(),
        val selectedCurrency: TransactionEditCurrency? = null,
        val amount: String = "",
        val rate: String = "",
    )
}