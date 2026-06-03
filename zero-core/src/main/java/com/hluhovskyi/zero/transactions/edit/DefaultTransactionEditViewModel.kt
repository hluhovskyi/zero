package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal class DefaultTransactionEditViewModel(
    private val useCase: TransactionEditUseCase,
    private val isEditMode: Boolean,
    private val isDuplicateMode: Boolean,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : TransactionEditViewModel {

    // Which field the inline keypad drives. A UI concern, so it lives here, not in the use case.
    private val keypadFocus = MutableStateFlow(TransactionEditFocusTarget.Amount)

    override val state: Flow<TransactionEditViewModel.State> =
        combine(useCase.state, keypadFocus) { state, focus ->
            TransactionEditViewModel.State(
                selectedTransactionType = state.transactionType,
                headerMode = headerMode(state),
                notes = state.notes,
                amount = state.amount,
                rate = state.rate,
                targetAmount = state.targetAmount,
                currencySymbol = state.sourceCurrencySymbol(),
                canPickCurrency = state.transactionType != TransactionEditType.TRANSFER,
                // Duplicating is a save-a-copy flow, so keep its button available immediately;
                // for new/edit, only reveal the button once the user actually changes something.
                isSaveVisible = state.isModified || isDuplicateMode,
                keypadTarget = if (state.hasFx()) focus else TransactionEditFocusTarget.Amount,
            )
        }.distinctUntilChanged()

    override val form: Flow<TransactionEditViewModel.Form> =
        combine(useCase.state, keypadFocus) { state, focus ->
            val keypadTarget = if (state.hasFx()) focus else TransactionEditFocusTarget.Amount
            when (state.transactionType) {
                TransactionEditType.EXPENSE, TransactionEditType.INCOME -> expenseIncomeForm(state, keypadTarget)
                TransactionEditType.TRANSFER -> transferForm(state, keypadTarget)
            }
        }.distinctUntilChanged()

    override fun perform(action: TransactionEditViewModel.Action) {
        when (action) {
            is TransactionEditViewModel.Action.FocusAmount -> keypadFocus.value = TransactionEditFocusTarget.Amount
            is TransactionEditViewModel.Action.FocusRate -> keypadFocus.value = TransactionEditFocusTarget.Rate
            is TransactionEditViewModel.Action.FocusReceived -> keypadFocus.value = TransactionEditFocusTarget.Received

            // Picking an account/currency or switching type re-derives the rate, so return focus to the amount.
            is TransactionEditViewModel.Action.ChangeTransactionType -> {
                keypadFocus.value = TransactionEditFocusTarget.Amount
                useCase.perform(TransactionEditUseCase.Action.SwitchTransaction(action.type))
            }
            is TransactionEditViewModel.Action.SelectAccount -> {
                keypadFocus.value = TransactionEditFocusTarget.Amount
                useCase.perform(TransactionEditUseCase.Action.SelectAccount(action.account))
            }
            is TransactionEditViewModel.Action.SelectTargetAccount -> {
                keypadFocus.value = TransactionEditFocusTarget.Amount
                useCase.perform(TransactionEditUseCase.Action.SelectTargetAccount(action.account))
            }
            is TransactionEditViewModel.Action.SwapAccounts -> {
                keypadFocus.value = TransactionEditFocusTarget.Amount
                useCase.perform(TransactionEditUseCase.Action.SwapAccounts)
            }
            is TransactionEditViewModel.Action.ResetRate -> {
                keypadFocus.value = TransactionEditFocusTarget.Amount
                useCase.perform(TransactionEditUseCase.Action.ResetRate)
            }

            is TransactionEditViewModel.Action.ChangeAmount ->
                useCase.perform(TransactionEditUseCase.Action.ChangeAmount(action.amount))
            is TransactionEditViewModel.Action.ChangeRate ->
                useCase.perform(TransactionEditUseCase.Action.ChangeRate(action.rate))
            is TransactionEditViewModel.Action.ChangeTargetAmount ->
                useCase.perform(TransactionEditUseCase.Action.ChangeTargetAmount(action.amount))
            is TransactionEditViewModel.Action.ChangeDate ->
                useCase.perform(TransactionEditUseCase.Action.ChangeDate(action.date))
            is TransactionEditViewModel.Action.ChangeNotes ->
                useCase.perform(TransactionEditUseCase.Action.ChangeNotes(action.notes))
            is TransactionEditViewModel.Action.SelectCategory ->
                useCase.perform(TransactionEditUseCase.Action.SelectCategory(action.category))
            is TransactionEditViewModel.Action.ShowAllCategories ->
                useCase.perform(TransactionEditUseCase.Action.ShowAllCategories)
            is TransactionEditViewModel.Action.PickCurrency ->
                useCase.perform(TransactionEditUseCase.Action.ShowAllCurrencies)
            is TransactionEditViewModel.Action.Save -> useCase.perform(TransactionEditUseCase.Action.Save)
            is TransactionEditViewModel.Action.Discard -> useCase.perform(TransactionEditUseCase.Action.Discard)
            is TransactionEditViewModel.Action.Delete -> useCase.perform(TransactionEditUseCase.Action.Delete)
            is TransactionEditViewModel.Action.Duplicate -> useCase.perform(TransactionEditUseCase.Action.Duplicate)
        }
    }

    private fun expenseIncomeForm(
        state: TransactionEditUseCase.State,
        keypadTarget: TransactionEditFocusTarget,
    ): TransactionEditViewModel.Form.ExpenseIncome {
        val accountCurrency = state.currencies.firstOrNull { it.id == state.selectedAccount?.currencyId }
        val targetSymbol = accountCurrency?.currencySymbol.orEmpty()
        val converted = if (state.hasFx()) {
            "≈ " + amountFormatter.format(
                Amount(state.amount.toBigDecimalOrNull()).withRate(Rate(state.rate.toBigDecimalOrNull())),
                targetSymbol,
            )
        } else {
            ""
        }
        return TransactionEditViewModel.Form.ExpenseIncome(
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            hasFx = state.hasFx(),
            rate = state.rate,
            rateAuto = state.rateAuto,
            keypadTarget = keypadTarget,
            sourceCurrencySymbol = state.sourceCurrencySymbol(),
            targetCurrencySymbol = targetSymbol,
            date = state.date,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            // Quick chips are a first-time shortcut: only for a new transaction, and only until the
            // user makes an explicit category choice (chip or picker).
            showCategoryShortcuts = !isEditMode && !state.categoryChosenByUser,
            convertedAmountText = converted,
            targetCurrencyName = accountCurrency?.name.orEmpty(),
        )
    }

    private fun transferForm(
        state: TransactionEditUseCase.State,
        keypadTarget: TransactionEditFocusTarget,
    ): TransactionEditViewModel.Form.Transfer = TransactionEditViewModel.Form.Transfer(
        accounts = state.accounts,
        selectedAccount = state.selectedAccount,
        hasFx = state.hasFx(),
        rate = state.rate,
        rateAuto = state.rateAuto,
        keypadTarget = keypadTarget,
        sourceCurrencySymbol = state.sourceCurrencySymbol(),
        targetCurrencySymbol = state.targetCurrencySymbol(),
        date = state.date,
        targetAccounts = state.targetAccounts,
        selectedTargetAccount = state.selectedTargetAccount,
        fromAmount = state.amount,
        toAmount = state.targetAmount,
    )

    private fun headerMode(state: TransactionEditUseCase.State): TransactionEditViewModel.HeaderMode = when {
        isDuplicateMode -> TransactionEditViewModel.HeaderMode.DuplicateFrom(subtitle = formatSubtitle(state))
        isEditMode -> TransactionEditViewModel.HeaderMode.Edit
        else -> TransactionEditViewModel.HeaderMode.New
    }

    private fun formatSubtitle(state: TransactionEditUseCase.State): String {
        val snapshot = state.sourceSnapshot ?: return ""
        val dateText = dateFormatter.format(
            date = snapshot.date.date,
            dayConfig = DateFormatter.DayConfig.WithoutZero,
            monthConfig = DateFormatter.MonthConfig.Readable,
            yearConfig = DateFormatter.YearConfig.Default,
        )
        val amountValue = snapshot.amount.toBigDecimalOrNull()
        return if (amountValue != null) {
            "${amountFormatter.format(Amount(amountValue), snapshot.currencySymbol)} · $dateText"
        } else {
            dateText
        }
    }

    // Source = tx currency (expense/income) or from-account currency (transfer).
    private fun TransactionEditUseCase.State.sourceCurrencySymbol(): String = if (transactionType == TransactionEditType.TRANSFER) {
        currencySymbolOf(selectedAccount)
    } else {
        selectedCurrency?.currencySymbol.orEmpty()
    }

    // Target = account currency (expense/income) or to-account currency (transfer).
    private fun TransactionEditUseCase.State.targetCurrencySymbol(): String = if (transactionType == TransactionEditType.TRANSFER) {
        currencySymbolOf(selectedTargetAccount)
    } else {
        currencySymbolOf(selectedAccount)
    }

    /** Whether the source and destination currencies differ, so the conversion UI should show. */
    private fun TransactionEditUseCase.State.hasFx(): Boolean = if (transactionType == TransactionEditType.TRANSFER) {
        selectedAccount != null &&
            selectedTargetAccount != null &&
            selectedAccount.currencyId != selectedTargetAccount.currencyId
    } else {
        selectedCurrency != null &&
            selectedAccount != null &&
            selectedCurrency.id != selectedAccount.currencyId
    }

    private fun TransactionEditUseCase.State.currencySymbolOf(account: TransactionEditAccount?): String = account?.let { currencies.firstOrNull { currency -> currency.id == it.currencyId }?.currencySymbol }.orEmpty()
}
