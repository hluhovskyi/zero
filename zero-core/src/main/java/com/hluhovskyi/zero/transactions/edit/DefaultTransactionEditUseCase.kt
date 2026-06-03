package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.math.BigDecimal

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
    private val incorrectStateDetector: IncorrectStateDetector,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val onDiscardHandler: OnDiscardHandler,
    private val onDuplicateHandler: OnDuplicateHandler,
    private val transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
    private val transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    logger: Logger,
) : TransactionEditUseCase {

    private val logger = logger.withTag(TAG)

    // The single editable intent. Written by `perform`, the pickers and the loader — never by the
    // reference collectors.
    private val mutableDraft = MutableStateFlow(
        TransactionEditDraft(
            accountId = preSelectedAccountId as? Id.Known,
            categoryId = preSelectedCategoryId as? Id.Known,
            pinSelectedCategory = preSelectedCategoryId is Id.Known,
        ),
    )

    // Reference lists, each filled by exactly one collector.
    private val accountsState = MutableStateFlow<List<TransactionEditAccount>>(emptyList())
    private val categoriesState = MutableStateFlow<List<TransactionEditCategory>>(emptyList())
    private val currenciesState = MutableStateFlow<List<TransactionEditCurrency>>(emptyList())

    // The resolved read model. Written by exactly one writer — the `combine` in `attach`.
    private val mutableState = MutableStateFlow(TransactionEditState())

    override val state: Flow<TransactionEditUseCase.State> = mutableState
        .map { state ->
            val categoryType = state.transactionType.categoryType()
            TransactionEditUseCase.State(
                transactionType = state.transactionType,
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                targetAccounts = state.targetAccounts,
                selectedTargetAccount = state.selectedTargetAccount,
                categories = categoryType?.let { type -> state.allCategories.filter { it.type == type } }.orEmpty(),
                selectedCategory = state.selectedCategory?.takeIf { it.type == categoryType },
                categoryPickedFromPicker = state.categoryPickedFromPicker,
                currencies = state.currencies,
                selectedCurrency = state.selectedCurrency,
                amount = state.amount,
                rate = state.rate,
                rateAuto = state.rateAuto,
                targetAmount = state.targetAmount,
                notes = state.notes,
                date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
                sourceSnapshot = state.sourceSnapshot,
                isModified = state.isModified,
            )
        }
        .distinctUntilChanged()

    override fun perform(action: TransactionEditUseCase.Action) {
        logger.d("perform=$action")
        if (action.isUserEdit()) {
            mutableDraft.update { it.copy(isModified = true) }
        }
        when (action) {
            is TransactionEditUseCase.Action.ChangeAmount -> mutableDraft.update { draft ->
                draft.copy(amount = action.amount, targetAmount = draft.receivedFor(action.amount, draft.rate))
            }

            is TransactionEditUseCase.Action.ChangeRate -> mutableDraft.update { draft ->
                draft.copy(
                    rate = action.rate,
                    rateAuto = false,
                    targetAmount = draft.receivedFor(draft.amount, action.rate),
                )
            }

            is TransactionEditUseCase.Action.ResetRate -> mutableDraft.update { it.copy(rateAuto = true) }

            // From is the anchor: editing the To amount re-derives the rate, leaving `amount` fixed.
            is TransactionEditUseCase.Action.ChangeTargetAmount -> mutableDraft.update { draft ->
                draft.copy(
                    targetAmount = action.amount,
                    rate = rateFromAmounts(draft.amount, action.amount) ?: draft.rate,
                    rateAuto = false,
                )
            }

            // Picking an account / target / swapping re-derives the rate, so float it again. The
            // currency follows the new account unless the user has explicitly picked one.
            is TransactionEditUseCase.Action.SelectAccount -> mutableDraft.update { draft ->
                draft.copy(
                    accountId = action.account.id,
                    rateAuto = true,
                    currencyId = if (draft.manuallyChangedCurrency) draft.currencyId else null,
                )
            }

            is TransactionEditUseCase.Action.SelectTargetAccount ->
                mutableDraft.update { it.copy(targetAccountId = action.account.id, rateAuto = true) }

            is TransactionEditUseCase.Action.SwapAccounts -> mutableDraft.update { draft ->
                draft.copy(accountId = draft.targetAccountId, targetAccountId = draft.accountId, rateAuto = true)
            }

            is TransactionEditUseCase.Action.SwitchTransaction ->
                mutableDraft.update { it.copy(transactionType = action.type) }

            is TransactionEditUseCase.Action.SelectCategory ->
                mutableDraft.update { it.copy(categoryId = action.category.id, pinSelectedCategory = false) }

            is TransactionEditUseCase.Action.SelectCurrency ->
                mutableDraft.update { it.copy(manuallyChangedCurrency = true, currencyId = action.currency.id) }

            is TransactionEditUseCase.Action.ChangeDate ->
                mutableDraft.update { it.copy(localDateTime = action.date) }

            is TransactionEditUseCase.Action.ChangeNotes ->
                mutableDraft.update { it.copy(notes = action.notes) }

            is TransactionEditUseCase.Action.ShowAllCategories ->
                transactionEditCategoryUseCase.perform(
                    TransactionEditCategoryUseCase.Action.Request(
                        selectedCategoryId = mutableState.value.selectedCategory?.id ?: Id.Unknown,
                    ),
                )

            is TransactionEditUseCase.Action.ShowAllCurrencies ->
                transactionEditCurrencyUseCase.perform(TransactionEditCurrencyUseCase.Action.Request)

            is TransactionEditUseCase.Action.EditCategories ->
                coroutineScope.launch(context = Dispatchers.Main) { onEditCategoriesHandler.onEdit() }

            is TransactionEditUseCase.Action.Discard ->
                coroutineScope.launch(context = Dispatchers.Main) { onDiscardHandler.onDiscard() }

            is TransactionEditUseCase.Action.Delete -> coroutineScope.launch {
                (transactionId as? Id.Known)?.let { id -> transactionRepository.delete(id) }
                launch(context = Dispatchers.Main) { onDiscardHandler.onDiscard() }
            }

            is TransactionEditUseCase.Action.Duplicate -> (transactionId as? Id.Known)?.let { id ->
                coroutineScope.launch(context = Dispatchers.Main) { onDuplicateHandler.onDuplicate(id) }
            }

            is TransactionEditUseCase.Action.Save -> save()
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            launch { loadTransaction() }

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
                    .collect { accountsState.value = it }
            }

            launch { collectRankedCategories() }

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
                    .collect { currenciesState.value = it }
            }

            launch(context = Dispatchers.Main) {
                transactionEditCategoryUseCase.state
                    .filterIsInstance<TransactionEditCategoryUseCase.State.Picked>()
                    .collect { picked ->
                        mutableDraft.update { it.copy(categoryId = picked.categoryId, pinSelectedCategory = false, categoryPickedFromPicker = true, isModified = true) }
                    }
            }

            launch(context = Dispatchers.Main) {
                transactionEditCurrencyUseCase.state
                    .filterIsInstance<TransactionEditCurrencyUseCase.State.Picked>()
                    .collect { picked ->
                        val currency = TransactionEditCurrency(
                            id = picked.currency.id,
                            name = picked.currency.name,
                            currencySymbol = picked.currency.symbol,
                        )
                        mutableDraft.update {
                            it.copy(
                                currencyId = currency.id,
                                manuallyChangedCurrency = true,
                                pickedCurrency = currency,
                                isModified = true,
                            )
                        }
                    }
            }

            launch { autoDeriveRateOnPairChange() }

            launch {
                combine(
                    accountsState,
                    categoriesState,
                    currenciesState,
                    mutableDraft,
                ) { accounts, categories, currencies, draft ->
                    resolve(draft, accounts, categories, currencies)
                }.collect { mutableState.value = it }
            }
        }
    }

    private suspend fun loadTransaction() {
        val loadFromId = (duplicateFromTransactionId as? Id.Known) ?: (transactionId as? Id.Known) ?: return
        val transaction = transactionRepository
            .query(TransactionRepository.Criteria.ById(loadFromId))
            .firstOrNull()
        if (transaction == null) {
            incorrectStateDetector.assert("Transaction is not resolved with id=$loadFromId")
            return
        }
        mutableDraft.update { applyLoaded(it, transaction, duplicateFromTransactionId is Id.Known) }
    }

    private suspend fun collectRankedCategories() {
        val accountSignals = combine(
            accountsState,
            mutableDraft.map { it.accountId }.distinctUntilChanged(),
        ) { accounts, accountId -> accountId ?: accounts.firstOrNull()?.id }
            .distinctUntilChanged()
            .map { CategoriesQueryUseCase.RankSignal.AccountChanged(it) }

        val dateSignals = mutableDraft
            .map { it.localDateTime?.date }
            .distinctUntilChanged()
            .map { CategoriesQueryUseCase.RankSignal.DateChanged(it) }

        val amountSignals = mutableDraft
            .map { it.amount.toBigDecimalOrNull()?.takeIf { value -> value > BigDecimal.ZERO } }
            .distinctUntilChanged()
            .map { CategoriesQueryUseCase.RankSignal.AmountChanged(it) }

        categoriesQueryUseCase.queryRanked(merge(accountSignals, dateSignals, amountSignals))
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
            .collect { categoriesState.value = it }
    }

    /**
     * Keeps the rate in sync with the active currency pair while the user hasn't pinned it —
     * re-deriving whenever the pair changes or the rate returns to auto. Reads the resolved pair,
     * writes the derived rate back into the draft (which the resolver then reflects).
     */
    private suspend fun autoDeriveRateOnPairChange() {
        mutableState
            .map { RateKey(it.currencyPair(), it.rateAuto) }
            .distinctUntilChanged()
            .collectLatest { (pair, auto) ->
                val (sourceCurrencyId, targetCurrencyId) = pair
                if (!auto || sourceCurrencyId == null || targetCurrencyId == null || sourceCurrencyId == targetCurrencyId) {
                    return@collectLatest
                }
                val derivedRate = currencyConvertUseCase.getRate(sourceCurrencyId, targetCurrencyId).value.toRateString()
                mutableDraft.update { draft ->
                    if (!draft.rateAuto) draft else draft.copy(rate = derivedRate, targetAmount = draft.receivedFor(draft.amount, derivedRate))
                }
            }
    }

    private fun save() {
        coroutineScope.launch(context = Dispatchers.IO) {
            val id = (transactionId as? Id.Known) ?: idGenerator()
            val transaction = buildTransaction(mutableState.value, id, clock.localDateTime(zoneProvider.timeZone()))
                ?: return@launch // TODO: Validation message
            transactionRepository.insert(transaction)
            launch(context = Dispatchers.Main) {
                onTransactionSavedHandler.onSaved()
            }
        }
    }

    private data class RateKey(val pair: Pair<Id.Known?, Id.Known?>, val auto: Boolean)

    private fun TransactionEditState.currencyPair(): Pair<Id.Known?, Id.Known?> = if (transactionType == TransactionEditType.TRANSFER) {
        selectedAccount?.currencyId to selectedTargetAccount?.currencyId
    } else {
        selectedCurrency?.id to selectedAccount?.currencyId
    }

    /** The destination amount for a transfer (`from × rate`, money-scaled); unchanged otherwise. */
    private fun TransactionEditDraft.receivedFor(from: String, rate: String): String = if (transactionType == TransactionEditType.TRANSFER) receivedAmount(from, rate) else targetAmount

    /** Actions that change the form contents, as opposed to navigation / lifecycle actions. */
    private fun TransactionEditUseCase.Action.isUserEdit(): Boolean = when (this) {
        is TransactionEditUseCase.Action.SwitchTransaction,
        is TransactionEditUseCase.Action.SelectAccount,
        is TransactionEditUseCase.Action.SelectTargetAccount,
        is TransactionEditUseCase.Action.SelectCurrency,
        is TransactionEditUseCase.Action.SelectCategory,
        is TransactionEditUseCase.Action.ChangeAmount,
        is TransactionEditUseCase.Action.ChangeRate,
        is TransactionEditUseCase.Action.ChangeDate,
        is TransactionEditUseCase.Action.ChangeTargetAmount,
        is TransactionEditUseCase.Action.ResetRate,
        is TransactionEditUseCase.Action.SwapAccounts,
        is TransactionEditUseCase.Action.ChangeNotes,
        -> true

        is TransactionEditUseCase.Action.EditCategories,
        is TransactionEditUseCase.Action.ShowAllCategories,
        is TransactionEditUseCase.Action.ShowAllCurrencies,
        is TransactionEditUseCase.Action.Save,
        is TransactionEditUseCase.Action.Discard,
        is TransactionEditUseCase.Action.Delete,
        is TransactionEditUseCase.Action.Duplicate,
        -> false
    }
}
