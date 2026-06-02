package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.joinIdsToString
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.common.toBigDecimalOrZero
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import java.io.Closeable
import java.math.BigDecimal
import java.math.RoundingMode

private const val TAG = "DefaultTransactionEditUseCase"

internal class DefaultTransactionEditUseCase(
    private val transactionId: Id,
    private val duplicateFromTransactionId: Id = Id.Unknown,
    private val preSelectedCategoryId: Id = Id.Unknown,
    private val preSelectedAccountId: Id = Id.Unknown,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val idGenerator: IdGenerator,
    private val amountFormatter: AmountFormatter,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val onDiscardHandler: OnDiscardHandler,
    private val onDuplicateHandler: OnDuplicateHandler,
    private val transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
    private val transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    logger: Logger,
) : TransactionEditUseCase {

    private val logger = logger.withTag(TAG)

    private val mutableState = MutableStateFlow(CompositeState())
    override val state: Flow<TransactionEditUseCase.State> = mutableState
        .map { state ->
            val currenciesDiffer = state.selectedCurrency != null && state.selectedAccount != null &&
                state.selectedCurrency.id != state.selectedAccount.currencyId
            val acctSymbol = state.selectedAccount?.let { acc ->
                state.currencies.firstOrNull { it.id == acc.currencyId }?.currencySymbol
            }.orEmpty()
            val convertedText = if (currenciesDiffer) {
                "≈ " + amountFormatter.format(
                    Amount(state.amount.toBigDecimalOrNull()).withRate(Rate(state.rate.toBigDecimalOrNull())),
                    acctSymbol,
                )
            } else {
                ""
            }
            val effectiveTarget = if (currenciesDiffer) state.editTarget else TransactionEditFocusTarget.Amount
            when (state.transactionType) {
                TransactionEditType.EXPENSE -> TransactionEditUseCase.State.Expense(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.allCategories.filter { it.type == CategoryType.EXPENSE },
                    selectedCategory = state.selectedCategory?.takeIf { it.type == CategoryType.EXPENSE },
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    rateAuto = state.rateAuto,
                    editTarget = effectiveTarget,
                    convertedAmountText = convertedText,
                    notes = state.notes,
                    date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
                    sourceSnapshot = state.sourceSnapshot,
                )

                TransactionEditType.INCOME -> TransactionEditUseCase.State.Income(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.allCategories.filter { it.type == CategoryType.INCOME },
                    selectedCategory = state.selectedCategory?.takeIf { it.type == CategoryType.INCOME },
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    rateAuto = state.rateAuto,
                    editTarget = effectiveTarget,
                    convertedAmountText = convertedText,
                    notes = state.notes,
                    date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
                    sourceSnapshot = state.sourceSnapshot,
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
                        rate = state.rate,
                        rateAuto = state.rateAuto,
                        editTarget = state.editTarget,
                        sourceCurrencySymbol = sourceCurrencySymbol,
                        targetCurrencySymbol = targetCurrencySymbol,
                        notes = state.notes,
                        date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
                        sourceSnapshot = state.sourceSnapshot,
                    )
                }
            }
        }

    override fun perform(action: TransactionEditUseCase.Action) {
        logger.d("perform=$action")
        when (action) {
            is TransactionEditUseCase.Action.ChangeAmount -> {
                mutableState.update { state ->
                    val received = if (state.transactionType == TransactionEditType.TRANSFER) {
                        action.amount.timesRate(state.rate)
                    } else {
                        state.targetAmount
                    }
                    state.copy(
                        amount = action.amount,
                        targetAmount = received,
                        editTarget = TransactionEditFocusTarget.Amount,
                    )
                }
            }

            is TransactionEditUseCase.Action.ChangeRate -> {
                mutableState.update { state ->
                    val received = if (state.transactionType == TransactionEditType.TRANSFER) {
                        state.amount.timesRate(action.rate)
                    } else {
                        state.targetAmount
                    }
                    state.copy(rate = action.rate, rateAuto = false, targetAmount = received)
                }
            }

            is TransactionEditUseCase.Action.FocusAmount ->
                mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Amount) }

            is TransactionEditUseCase.Action.FocusRate ->
                mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Rate) }

            is TransactionEditUseCase.Action.FocusReceived ->
                mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Received) }

            is TransactionEditUseCase.Action.ResetRate ->
                mutableState.update {
                    it.copy(rateAuto = true, editTarget = TransactionEditFocusTarget.Amount)
                }

            is TransactionEditUseCase.Action.SelectAccount -> selectAccount(action)

            is TransactionEditUseCase.Action.SelectCategory -> {
                mutableState.update { state ->
                    state.copy(selectedCategory = action.category)
                }
            }

            is TransactionEditUseCase.Action.ShowAllCategories -> {
                transactionEditCategoryUseCase.perform(
                    TransactionEditCategoryUseCase.Action.Request(
                        selectedCategoryId = mutableState.value.selectedCategory?.id ?: Id.Unknown,
                    ),
                )
            }

            is TransactionEditUseCase.Action.ShowAllCurrencies -> {
                transactionEditCurrencyUseCase.perform(TransactionEditCurrencyUseCase.Action.Request)
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
                    state.copy(
                        selectedTargetAccount = action.account,
                        rateAuto = true,
                        editTarget = TransactionEditFocusTarget.Amount,
                    )
                }
            }

            is TransactionEditUseCase.Action.SwitchTransaction -> {
                mutableState.update { state ->
                    val targetType = when (action.type) {
                        TransactionEditType.EXPENSE -> CategoryType.EXPENSE
                        TransactionEditType.INCOME -> CategoryType.INCOME
                        TransactionEditType.TRANSFER -> null
                    }
                    val newSelected = if (targetType != null && state.selectedCategory?.type != targetType) {
                        state.allCategories.firstOrNull { it.type == targetType }
                    } else {
                        state.selectedCategory
                    }
                    state.copy(transactionType = action.type, selectedCategory = newSelected)
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

            is TransactionEditUseCase.Action.Discard -> {
                coroutineScope.launch(context = Dispatchers.Main) {
                    onDiscardHandler.onDiscard()
                }
            }

            is TransactionEditUseCase.Action.Delete -> {
                coroutineScope.launch {
                    (transactionId as? Id.Known)?.let { id ->
                        transactionRepository.delete(id)
                    }
                    launch(context = Dispatchers.Main) {
                        onDiscardHandler.onDiscard()
                    }
                }
            }

            is TransactionEditUseCase.Action.ChangeTargetAmount -> {
                mutableState.update { state ->
                    // From is the anchor: editing the To amount re-derives the rate, leaving `amount` fixed.
                    val newRate = rateFromAmounts(state.amount, action.amount) ?: state.rate
                    state.copy(
                        targetAmount = action.amount,
                        rate = newRate,
                        rateAuto = false,
                        editTarget = TransactionEditFocusTarget.Received,
                    )
                }
            }

            is TransactionEditUseCase.Action.ChangeNotes -> {
                mutableState.update { state ->
                    state.copy(notes = action.notes)
                }
            }

            is TransactionEditUseCase.Action.Save -> save()
            is TransactionEditUseCase.Action.SwapAccounts -> swapAccounts()
            is TransactionEditUseCase.Action.Duplicate -> {
                (transactionId as? Id.Known)?.let { id ->
                    coroutineScope.launch(context = Dispatchers.Main) {
                        onDuplicateHandler.onDuplicate(id)
                    }
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val loadFromId = (duplicateFromTransactionId as? Id.Known)
                ?: (transactionId as? Id.Known)
            if (loadFromId != null) {
                launch {
                    incorrectStateDetector.asyncRequireNonNull(
                        value = transactionRepository.query(
                            TransactionRepository.Criteria.ById(
                                loadFromId,
                            ),
                        )
                            .firstOrNull(),
                        message = "Transaction is not resolved with id=$loadFromId",
                    ) { transaction ->
                        logger.d("attach, transaction loading is finished, transactionId=${transaction.id}")

                        mutableState
                            .filter { state ->
                                state.accounts.isNotEmpty() &&
                                    state.allCategories.isNotEmpty() &&
                                    state.currencies.isNotEmpty()
                            }
                            .take(1)
                            .collect()

                        logger.d("attach, required data for state is loaded")
                        mutableState.update { state ->
                            val accountToSelect =
                                state.accounts.firstOrNull { it.id == transaction.accountId }
                            val currencyToSelect =
                                state.currencies.firstOrNull { it.id == transaction.currencyId }
                            val snapshot = if (duplicateFromTransactionId is Id.Known) {
                                TransactionEditUseCase.SourceSnapshot(
                                    amount = transaction.amount.value.toString(),
                                    date = transaction.dateTime,
                                    currencySymbol = currencyToSelect?.currencySymbol.orEmpty(),
                                )
                            } else {
                                null
                            }
                            val partialState = state.copy(
                                amount = transaction.amount.value.toString(),
                                selectedCurrency = currencyToSelect ?: state.selectedCurrency,
                                selectedAccount = accountToSelect ?: state.selectedAccount,
                                localDateTime = transaction.dateTime,
                                notes = transaction.notes.orEmpty(),
                                sourceSnapshot = snapshot,
                            )

                            when (transaction) {
                                is TransactionRepository.Transaction.Expense -> {
                                    val (categoryToSelect, reorderedCategories) =
                                        resolveCategoryForEdit(state.allCategories, transaction.categoryId)

                                    partialState.copy(
                                        transactionType = TransactionEditType.EXPENSE,
                                        allCategories = reorderedCategories,
                                        selectedCategory = categoryToSelect
                                            ?: state.selectedCategory,
                                        rate = transaction.rate.value.toString(),
                                        rateAuto = false,
                                    )
                                }

                                is TransactionRepository.Transaction.Income -> {
                                    val (categoryToSelect, reorderedCategories) =
                                        resolveCategoryForEdit(state.allCategories, transaction.categoryId)

                                    partialState.copy(
                                        transactionType = TransactionEditType.INCOME,
                                        allCategories = reorderedCategories,
                                        selectedCategory = categoryToSelect
                                            ?: state.selectedCategory,
                                        rate = transaction.rate.value.toString(),
                                        rateAuto = false,
                                    )
                                }

                                is TransactionRepository.Transaction.Transfer -> {
                                    val targetAccountToSelect =
                                        state.accounts.firstOrNull { it.id == transaction.targetAccount }
                                    val toAmount = transaction.targetAmount.value.toString()

                                    partialState.copy(
                                        transactionType = TransactionEditType.TRANSFER,
                                        selectedTargetAccount = targetAccountToSelect,
                                        targetAmount = toAmount,
                                        rate = rateFromAmounts(partialState.amount, toAmount) ?: "1",
                                        rateAuto = false,
                                    )
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
                                currencyId = account.currencyId,
                            )
                        }
                    }
                    .collectLatest { accounts ->
                        mutableState.update { state ->
                            val preSelected = (preSelectedAccountId as? Id.Known)
                                ?.let { id -> accounts.find { it.id == id } }
                            val accountToSelect = preSelected ?: accounts.firstOrNull()
                            logger.d("attach, accounts=${accounts.joinIdsToString()}")
                            state.copy(
                                accounts = accounts,
                                selectedAccount = state.selectedAccount ?: accountToSelect,
                                targetAccounts = accounts,
                                selectedTargetAccount = state.selectedTargetAccount
                                    ?: accounts.firstOrNull(),
                                selectedCurrency = if (state.manuallyChangedCurrency) {
                                    state.selectedCurrency
                                } else {
                                    accountToSelect?.let { account -> state.currencies.firstOrNull { it.id == account.currencyId } }
                                },
                            )
                        }
                    }
            }

            launch {
                val accountSignals = mutableState
                    .map { it.selectedAccount?.id }
                    .distinctUntilChanged()
                    .map { CategoriesQueryUseCase.RankSignal.AccountChanged(it) }

                val dateSignals = mutableState
                    .map { it.localDateTime?.date }
                    .distinctUntilChanged()
                    .map { CategoriesQueryUseCase.RankSignal.DateChanged(it) }

                val amountSignals = mutableState
                    .map { it.amount.toBigDecimalOrNull()?.takeIf { value -> value > BigDecimal.ZERO } }
                    .distinctUntilChanged()
                    .map { CategoriesQueryUseCase.RankSignal.AmountChanged(it) }

                val signals = merge(accountSignals, dateSignals, amountSignals)

                categoriesQueryUseCase.queryRanked(signals)
                    .map { categories ->
                        categories.map { category ->
                            TransactionEditCategory(
                                id = category.id,
                                name = category.name,
                                colorScheme = category.colorScheme,
                                icon = category.icon,
                                type = category.type,
                            )
                        }
                    }
                    .collectLatest { categories ->
                        logger.d("attach, categories=${categories.joinToString { it.id.value }}")
                        mutableState.update { state ->
                            if (state.selectedCategory != null) {
                                val updated = categories.find { it.id == state.selectedCategory.id }
                                state.copy(
                                    allCategories = categories,
                                    selectedCategory = if (updated != state.selectedCategory) updated else state.selectedCategory,
                                )
                            } else {
                                val preSelected = (preSelectedCategoryId as? Id.Known)
                                    ?.let { id -> categories.find { it.id == id } }
                                val reordered = if (preSelected != null) {
                                    listOf(preSelected) + categories.filter { it.id != preSelected.id }
                                } else {
                                    categories
                                }
                                state.copy(
                                    allCategories = reordered,
                                    selectedCategory = preSelected ?: reordered.firstOrNull { it.type == CategoryType.EXPENSE },
                                )
                            }
                        }
                    }
            }

            launch {
                currencyRepository.query(CurrencyRepository.Criteria.InUse())
                    .map { currencies ->
                        currencies.map { currency ->
                            TransactionEditCurrency(
                                id = currency.id,
                                name = currency.name,
                                currencySymbol = currency.symbol,
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

            launch(context = Dispatchers.Main) {
                transactionEditCategoryUseCase.state
                    .filterIsInstance<TransactionEditCategoryUseCase.State.Picked>()
                    .collectLatest { picked ->
                        mutableState.update { state ->
                            val category = state.allCategories.firstOrNull { it.id == picked.categoryId }
                            if (category != null) state.copy(selectedCategory = category) else state
                        }
                    }
            }

            launch(context = Dispatchers.Main) {
                transactionEditCurrencyUseCase.state
                    .filterIsInstance<TransactionEditCurrencyUseCase.State.Picked>()
                    .collectLatest { picked ->
                        mutableState.update { state ->
                            val pickedCurrency = TransactionEditCurrency(
                                id = picked.currency.id,
                                name = picked.currency.name,
                                currencySymbol = picked.currency.symbol,
                            )
                            val currencies = if (state.currencies.any { it.id == pickedCurrency.id }) {
                                state.currencies
                            } else {
                                state.currencies + pickedCurrency
                            }
                            state.copy(
                                currencies = currencies,
                                manuallyChangedCurrency = true,
                                selectedCurrency = pickedCurrency,
                            )
                        }
                    }
            }

            // Auto-derive the exchange rate from the active currency pair whenever it changes
            // (or the field returns to auto). Expense/income pair = (tx currency, account currency);
            // transfer pair = (source account currency, target account currency).
            launch {
                mutableState
                    .map { state ->
                        val (src, dst) = state.ratePair()
                        listOf(state.transactionType, src, dst, state.rateAuto)
                    }
                    .distinctUntilChanged()
                    .collectLatest {
                        val current = mutableState.value
                        if (!current.rateAuto) return@collectLatest
                        val (src, dst) = current.ratePair()
                        if (src == null || dst == null || src == dst) return@collectLatest
                        val rate = currencyConvertUseCase.getRate(src, dst).value.format()
                        mutableState.update { state ->
                            if (!state.rateAuto) return@update state
                            val received = if (state.transactionType == TransactionEditType.TRANSFER) {
                                state.amount.timesRate(rate)
                            } else {
                                state.targetAmount
                            }
                            state.copy(rate = rate, targetAmount = received)
                        }
                    }
            }
        }
    }

    private fun CompositeState.ratePair(): Pair<Id.Known?, Id.Known?> =
        if (transactionType == TransactionEditType.TRANSFER) {
            selectedAccount?.currencyId to selectedTargetAccount?.currencyId
        } else {
            selectedCurrency?.id to selectedAccount?.currencyId
        }

    private fun selectAccount(action: TransactionEditUseCase.Action.SelectAccount) {
        mutableState.update { state ->
            state.copy(
                selectedAccount = action.account,
                rateAuto = true,
                editTarget = TransactionEditFocusTarget.Amount,
            )
        }
    }

    private fun save() {
        coroutineScope.launch(context = Dispatchers.IO) {
            val state = mutableState.value
            val transactionId = (transactionId as? Id.Known) ?: idGenerator()
            val dateTime = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone())
            val account = state.selectedAccount ?: return@launch // TODO: Validation message

            val transaction = when (state.transactionType) {
                TransactionEditType.EXPENSE -> {
                    val category = state.selectedCategory ?: return@launch
                    val currency = state.selectedCurrency

                    TransactionRepository.Transaction.Expense(
                        id = transactionId,
                        amount = Amount(state.amount.toBigDecimalOrNull()),
                        accountId = account.id,
                        currencyId = currency?.id ?: account.currencyId,
                        categoryId = category.id,
                        dateTime = dateTime,
                        updatedDateTime = clock.localDateTime(zoneProvider.timeZone()),
                        rate = Rate(state.rate.toBigDecimalOrNull()),
                        notes = state.notes.ifBlank { null },
                    )
                }

                TransactionEditType.INCOME -> {
                    val category = state.selectedCategory ?: return@launch
                    val currency = state.selectedCurrency

                    TransactionRepository.Transaction.Income(
                        id = transactionId,
                        amount = Amount(state.amount.toBigDecimalOrNull()),
                        accountId = account.id,
                        currencyId = currency?.id ?: account.currencyId,
                        categoryId = category.id,
                        dateTime = dateTime,
                        updatedDateTime = clock.localDateTime(zoneProvider.timeZone()),
                        rate = Rate(state.rate.toBigDecimalOrNull()),
                        notes = state.notes.ifBlank { null },
                    )
                }

                TransactionEditType.TRANSFER -> {
                    val targetAccount = state.selectedTargetAccount ?: return@launch

                    val sourceAmount = Amount(state.amount.toBigDecimalOrNull())
                    val computedTargetAmount = Amount(state.targetAmount.toBigDecimalOrNull())

                    TransactionRepository.Transaction.Transfer(
                        id = transactionId,
                        amount = sourceAmount,
                        accountId = account.id,
                        currencyId = account.currencyId,
                        targetAccount = targetAccount.id,
                        dateTime = dateTime,
                        updatedDateTime = clock.localDateTime(zoneProvider.timeZone()),
                        targetAmount = computedTargetAmount,
                        notes = state.notes.ifBlank { null },
                    )
                }
            }

            transactionRepository.insert(transaction)
            launch(context = Dispatchers.Main) {
                onTransactionSavedHandler.onSaved()
            }
        }
    }

    private fun swapAccounts() {
        mutableState.update { state ->
            state.copy(
                selectedAccount = state.selectedTargetAccount,
                selectedTargetAccount = state.selectedAccount,
                rateAuto = true,
                editTarget = TransactionEditFocusTarget.Amount,
            )
        }
    }

    private fun resolveCategoryForEdit(
        categories: List<TransactionEditCategory>,
        categoryId: Id.Known,
    ): Pair<TransactionEditCategory?, List<TransactionEditCategory>> {
        val categoryToSelect = categories.firstOrNull { it.id == categoryId }
        val reorderedCategories = if (categoryToSelect != null) {
            listOf(categoryToSelect) + categories.filter { it.id != categoryToSelect.id }
        } else {
            categories
        }
        return categoryToSelect to reorderedCategories
    }

    private fun BigDecimal.format() = setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    /** To amount = from × rate (2 dp). */
    private fun String.timesRate(rate: String): String =
        (toBigDecimalOrZero() * rate.toBigDecimalOrZero()).format()

    /** rate = to ÷ from (6 dp). Returns null when `from` is 0/blank so the caller keeps the old rate. */
    private fun rateFromAmounts(from: String, to: String): String? {
        val f = from.toBigDecimalOrNull() ?: return null
        if (f.signum() == 0) return null
        return to.toBigDecimalOrZero().divide(f, 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private data class CompositeState(
        val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val allCategories: List<TransactionEditCategory> = emptyList(),
        val selectedCategory: TransactionEditCategory? = null,
        val currencies: List<TransactionEditCurrency> = emptyList(),
        val selectedCurrency: TransactionEditCurrency? = null,
        val localDateTime: LocalDateTime? = null,
        val manuallyChangedCurrency: Boolean = false,
        val amount: String = "",
        val rate: String = "",
        val rateAuto: Boolean = true,
        val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
        val targetAmount: String = "",
        val notes: String = "",
        val sourceSnapshot: TransactionEditUseCase.SourceSnapshot? = null,
    )
}
