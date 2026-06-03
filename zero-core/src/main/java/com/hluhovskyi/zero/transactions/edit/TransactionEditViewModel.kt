package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface TransactionEditViewModel : ActionStateModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    /**
     * The type-specific form, exposed as its own distinct flow so the form composable recomposes
     * only when its own data changes — not when the header/amount/keypad ([state]) change.
     */
    val form: Flow<Form>

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action

        // The ViewProvider routes a keypad digit to one of these by [State.keypadTarget].
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        data class ChangeTargetAmount(val amount: String) : Action

        data class ChangeDate(val date: LocalDateTime) : Action
        data class ChangeNotes(val notes: String) : Action
        data class SelectCategory(val category: TransactionEditCategory) : Action
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        object SwapAccounts : Action
        object ShowAllCategories : Action
        object PickCurrency : Action
        object ResetRate : Action
        object FocusAmount : Action
        object FocusRate : Action
        object FocusReceived : Action
        object Save : Action
        object Discard : Action
        object Delete : Action
        object Duplicate : Action
    }

    /** Chrome: the pinned header, type toggle, hero amount, and keypad routing. */
    data class State(
        val transactionTypes: List<TransactionEditType> = TransactionEditType.values().toList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val headerMode: HeaderMode = HeaderMode.New,
        val notes: String = "",
        val amount: String = "",
        val rate: String = "",
        val targetAmount: String = "",
        val currencySymbol: String = "",
        val canPickCurrency: Boolean = false,
        val isSaveVisible: Boolean = false,
        val keypadTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
    )

    /** Per-type form. Shared fields live on the interface; each variant adds its specifics. */
    sealed interface Form {
        val accounts: List<TransactionEditAccount>
        val selectedAccount: TransactionEditAccount?
        val hasFx: Boolean
        val rate: String
        val rateAuto: Boolean
        val keypadTarget: TransactionEditFocusTarget
        val sourceCurrencySymbol: String
        val targetCurrencySymbol: String
        val date: LocalDateTime?

        data class ExpenseIncome(
            override val accounts: List<TransactionEditAccount> = emptyList(),
            override val selectedAccount: TransactionEditAccount? = null,
            override val hasFx: Boolean = false,
            override val rate: String = "",
            override val rateAuto: Boolean = true,
            override val keypadTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
            override val sourceCurrencySymbol: String = "",
            override val targetCurrencySymbol: String = "",
            override val date: LocalDateTime? = null,
            val categories: List<TransactionEditCategory> = emptyList(),
            val selectedCategory: TransactionEditCategory? = null,
            val convertedAmountText: String = "",
            val targetCurrencyName: String = "",
        ) : Form

        data class Transfer(
            override val accounts: List<TransactionEditAccount> = emptyList(),
            override val selectedAccount: TransactionEditAccount? = null,
            override val hasFx: Boolean = false,
            override val rate: String = "",
            override val rateAuto: Boolean = true,
            override val keypadTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
            override val sourceCurrencySymbol: String = "",
            override val targetCurrencySymbol: String = "",
            override val date: LocalDateTime? = null,
            val targetAccounts: List<TransactionEditAccount> = emptyList(),
            val selectedTargetAccount: TransactionEditAccount? = null,
            val fromAmount: String = "",
            val toAmount: String = "",
        ) : Form
    }

    sealed interface HeaderMode {
        object New : HeaderMode
        object Edit : HeaderMode
        data class DuplicateFrom(val subtitle: String) : HeaderMode
    }
}
