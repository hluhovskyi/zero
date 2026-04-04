package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.joinIdsToString
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

private const val TAG = "DefaultTransactionEditUseCase"

internal class DefaultTransactionEditUseCase(
    private val transactionId: Id,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val idGenerator: IdGenerator,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val onDiscardHandler: OnDiscardHandler,
    private val clock: Clock,
    private val incorrectStateDetector: IncorrectStateDetector,
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
                    rate = state.rate,
                    date = state.localDateTime ?: clock.localDateTime()
                )
                TransactionEditType.INCOME -> TransactionEditUseCase.State.Income(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    date = state.localDateTime ?: clock.localDateTime()
                )
                TransactionEditType.TRANSFER -> {
                    val sourceCurrencySymbol = state.selectedAccount?.let { account ->
                        state.currencies.firstOrNull { it.id == account.currencyId }?.currencySymbol
                    } ?: ""
                    val targetCurrencySymbol = state.selectedTargetAccount?.let { account ->
                        state.currencies.firstOrNull { it.id == account.currencyId }?.currencySymbol
                    } ?: ""
                    TransactionEditUseCase.State.Transfer(
                        accounts = state.accounts,
                        selectedAccount = state.selectedAccount,
                        targetAccounts = state.targetAccounts,
                        selectedTargetAccount = state.selectedTargetAccount,
                        amount = state.amount,
                        targetAmount = state.targetAmount,
                        transferRateMode = state.transferRateMode,
                        sourceCurrencySymbol = sourceCurrencySymbol,
                        targetCurrencySymbol = targetCurrencySymbol,
                        date = state.localDateTime ?: clock.localDateTime()
                    )
                }
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
                if (mutableState.value.transactionType == TransactionEditType.TRANSFER) {
                    coroutineScope.launch {
                        val rate = fetchRate(action.account, mutableState.value.selectedTargetAccount)
                        if (rate != null) {
                            mutableState.update { state ->
                                state.copy(transferRateMode = TransferRateMode.Default(rate))
                            }
                        }
                    }
                }
            }
            is TransactionEditUseCase.Action.SelectCategory -> {
                mutableState.update { state ->
                    state.copy(selectedCategory = action.category)
                }
            }
            is TransactionEditUseCase.Action.SelectCurrency -> {
                mutableState.update { state ->
                    state.copy(
                        manuallyChangedCurrency = true,
                        selectedCurrency = action.currency,
                    )
                }
            }
            is TransactionEditUseCase.Action.SelectTargetAccount -> {
                mutableState.update { state ->
                    state.copy(selectedTargetAccount = action.account)
                }
                if (mutableState.value.transactionType == TransactionEditType.TRANSFER) {
                    coroutineScope.launch {
                        val rate = fetchRate(mutableState.value.selectedAccount, action.account)
                        if (rate != null) {
                            mutableState.update { state ->
                                state.copy(transferRateMode = TransferRateMode.Default(rate))
                            }
                        }
                    }
                }
            }
            is TransactionEditUseCase.Action.SwitchTransaction -> {
                mutableState.update { state ->
                    state.copy(transactionType = action.type)
                }
            }
            is TransactionEditUseCase.Action.ChangeDate -> {
                mutableState.update { state ->
                    state.copy(localDateTime = action.date)
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
                    val transactionId = (transactionId as? Id.Known) ?: idGenerator()
                    val dateTime = state.localDateTime ?: clock.localDateTime()
                    val transaction = when (state.transactionType) {
                        TransactionEditType.EXPENSE -> {
                            val category = state.selectedCategory ?: return@launch
                            val account = state.selectedAccount ?: return@launch
                            val currency = state.selectedCurrency

                            TransactionRepository.Transaction.Expense(
                                id = transactionId,
                                amount = Amount(state.amount.toBigDecimalOrNull()),
                                accountId = account.id,
                                currencyId = currency?.id ?: account.currencyId,
                                categoryId = category.id,
                                dateTime = dateTime,
                                rate = Rate(state.rate.toBigDecimalOrNull())
                            )
                        }
                        TransactionEditType.INCOME -> {
                            val category = state.selectedCategory ?: return@launch
                            val account = state.selectedAccount ?: return@launch
                            val currency = state.selectedCurrency

                            TransactionRepository.Transaction.Income(
                                id = transactionId,
                                amount = Amount(state.amount.toBigDecimalOrNull()),
                                accountId = account.id,
                                currencyId = currency?.id ?: account.currencyId,
                                categoryId = category.id,
                                dateTime = dateTime,
                                rate = Rate(state.rate.toBigDecimalOrNull())
                            )
                        }
                        TransactionEditType.TRANSFER -> {
                            val account = state.selectedAccount ?: return@launch
                            val targetAccount = state.selectedTargetAccount ?: return@launch

                            val sourceAmount = Amount(state.amount.toBigDecimalOrNull())
                            val computedTargetAmount = when (val mode = state.transferRateMode) {
                                is TransferRateMode.Default -> sourceAmount.withRate(mode.rate)
                                is TransferRateMode.CustomRate -> {
                                    val customRate = Rate(mode.rate.toBigDecimalOrNull())
                                    sourceAmount.withRate(customRate)
                                }
                                is TransferRateMode.CustomAmount -> {
                                    Amount(state.targetAmount.toBigDecimalOrNull())
                                }
                            }

                            TransactionRepository.Transaction.Transfer(
                                id = transactionId,
                                amount = sourceAmount,
                                accountId = account.id,
                                currencyId = account.currencyId,
                                targetAccount = targetAccount.id,
                                dateTime = dateTime,
                                targetAmount = computedTargetAmount
                            )
                        }
                    }

                    transactionRepository.insert(transaction)
                    launch(context = Dispatchers.Main) {
                        onTransactionSavedHandler.onSaved()
                    }
                }
            }
            is TransactionEditUseCase.Action.Discard -> {
                coroutineScope.launch(context = Dispatchers.Main) {
                    onDiscardHandler.onDiscard()
                }
            }
            is TransactionEditUseCase.Action.ChangeTargetAmount -> {
                mutableState.update { state ->
                    state.copy(targetAmount = action.amount)
                }
            }
            is TransactionEditUseCase.Action.ChangeTransferRate -> {
                mutableState.update { state ->
                    state.copy(
                        transferRateMode = TransferRateMode.CustomRate(action.rate)
                    )
                }
            }
            is TransactionEditUseCase.Action.CycleTransferRateMode -> {
                mutableState.update { state ->
                    var nextTargetAmount = state.targetAmount
                    val nextMode = when (val currentMode = state.transferRateMode) {
                        is TransferRateMode.Default -> {
                            val rateStr = currentMode.rate.value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            TransferRateMode.CustomRate(rateStr)
                        }
                        is TransferRateMode.CustomRate -> {
                            val sourceAmount = state.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            val rate = currentMode.rate.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            nextTargetAmount = sourceAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            TransferRateMode.CustomAmount(nextTargetAmount)
                        }
                        is TransferRateMode.CustomAmount -> TransferRateMode.Default(Rate.Same)
                    }
                    state.copy(
                        transferRateMode = nextMode,
                        targetAmount = nextTargetAmount
                    )
                }
                // If cycling back to Default, re-fetch the rate
                val currentState = mutableState.value
                if (currentState.transferRateMode is TransferRateMode.Default) {
                    coroutineScope.launch {
                        val rate = fetchRate(currentState.selectedAccount, currentState.selectedTargetAccount)
                        if (rate != null) {
                            mutableState.update { state ->
                                state.copy(transferRateMode = TransferRateMode.Default(rate))
                            }
                        }
                    }
                }
            }
            is TransactionEditUseCase.Action.SwapAccounts -> {
                mutableState.update { state ->
                    state.copy(
                        selectedAccount = state.selectedTargetAccount,
                        selectedTargetAccount = state.selectedAccount,
                    )
                }
                val currentState = mutableState.value
                coroutineScope.launch {
                    val rate = fetchRate(currentState.selectedAccount, currentState.selectedTargetAccount)
                    if (rate != null) {
                        mutableState.update { state ->
                            state.copy(transferRateMode = TransferRateMode.Default(rate))
                        }
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            if (transactionId is Id.Known) {
                launch {
                    incorrectStateDetector.asyncRequireNonNull(
                        value = transactionRepository.query(TransactionRepository.Criteria.ById(transactionId))
                            .firstOrNull(),
                        message = "Transaction is not resolved with transactionId=$transactionId",
                    ) { transaction ->
                        logger.d("attach, transaction loading is finished, transactionId=${transaction.id}")

                        mutableState
                            .filter { state ->
                                state.accounts.isNotEmpty() &&
                                        state.categories.isNotEmpty() &&
                                        state.currencies.isNotEmpty()
                            }
                            .take(1)
                            .collect()

                        logger.d("attach, required data for state is loaded")
                        mutableState.update { state ->
                            val accountToSelect = state.accounts.firstOrNull { it.id == transaction.accountId }
                            val currencyToSelect = state.currencies.firstOrNull { it.id == transaction.currencyId }
                            val partialState = state.copy(
                                amount = transaction.amount.value.toString(),
                                selectedCurrency = currencyToSelect ?: state.selectedCurrency,
                                selectedAccount = accountToSelect ?: state.selectedAccount,
                                localDateTime = transaction.dateTime,
                            )

                            when (transaction) {
                                is TransactionRepository.Transaction.Expense -> {
                                    val categoryToSelect =
                                        state.categories.firstOrNull { it.id == transaction.categoryId }

                                    partialState.copy(
                                        transactionType = TransactionEditType.EXPENSE,
                                        selectedCategory = categoryToSelect ?: state.selectedCategory,
                                        rate = transaction.rate.value.toString(),
                                    )
                                }

                                is TransactionRepository.Transaction.Income -> {
                                    val categoryToSelect =
                                        state.categories.firstOrNull { it.id == transaction.categoryId }

                                    partialState.copy(
                                        transactionType = TransactionEditType.INCOME,
                                        selectedCategory = categoryToSelect ?: state.selectedCategory,
                                        rate = transaction.rate.value.toString()
                                    )
                                }

                                is TransactionRepository.Transaction.Transfer -> {
                                    val targetAccountToSelect =
                                        state.accounts.firstOrNull { it.id == transaction.targetAccount }

                                    val finalState = partialState.copy(
                                        transactionType = TransactionEditType.TRANSFER,
                                        selectedTargetAccount = targetAccountToSelect,
                                    )

                                    coroutineScope.launch {
                                        val rate = fetchRate(
                                            sourceAccount = finalState.selectedAccount,
                                            targetAccount = finalState.selectedTargetAccount
                                        )
                                        if (rate != null) {
                                            mutableState.update { state ->
                                                state.copy(transferRateMode = TransferRateMode.Default(rate))
                                            }
                                        }
                                    }

                                    finalState
                                }
                            }
                        }
                    }
                }
            }

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
                        mutableState.update { state ->
                            val accountToSelect = accounts.firstOrNull()
                            logger.d("attach, accounts=${accounts.joinIdsToString()}")
                            state.copy(
                                accounts = accounts,
                                selectedAccount = state.selectedAccount ?: accountToSelect,
                                targetAccounts = accounts,
                                selectedTargetAccount = state.selectedTargetAccount ?: accounts.firstOrNull(),
                                selectedCurrency = if (state.manuallyChangedCurrency) {
                                    state.selectedCurrency
                                } else {
                                    accountToSelect?.let { account -> state.currencies.firstOrNull { it.id == account.currencyId } }
                                }
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
                                colorScheme = category.colorScheme,
                                icon = category.icon,
                            )
                        }
                    }
                    .collectLatest { categories ->
                        logger.d("attach, categories=${categories.joinIdsToString()}")
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
                        logger.d("attach, currencies=${currencies.joinIdsToString()}")
                        mutableState.update { state ->
                            state.copy(
                                currencies = currencies,
                                selectedCurrency = if (state.manuallyChangedCurrency) {
                                    state.selectedCurrency
                                } else {
                                    state.selectedAccount
                                        ?.let { account -> currencies.firstOrNull { it.id == account.currencyId } }
                                        ?: currencies.firstOrNull()
                                },
                            )
                        }
                    }
            }
        }
    }

    private suspend fun fetchRate(
        sourceAccount: TransactionEditAccount?,
        targetAccount: TransactionEditAccount?
    ): Rate? {
        if (sourceAccount == null || targetAccount == null) return null
        if (sourceAccount.currencyId == targetAccount.currencyId) {
            return Rate.Same
        }
        return currencyConvertUseCase.getRate(sourceAccount.currencyId, targetAccount.currencyId)
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
        val localDateTime: LocalDateTime? = null,
        val manuallyChangedCurrency: Boolean = false,
        val amount: String = "",
        val rate: String = "",
        val targetAmount: String = "",
        val transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same),
    )
}
