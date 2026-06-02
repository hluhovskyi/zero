package com.hluhovskyi.zero.transactions.edit.common

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import kotlinx.datetime.LocalDateTime

interface TransactionEditExpenseIncomeViewModel : ActionStateModel<TransactionEditExpenseIncomeViewModel.Action, TransactionEditExpenseIncomeViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectCurrency(val currency: TransactionEditCurrency) : Action
        data class SelectCategory(val category: TransactionEditCategory) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object FocusRate : Action
        object ResetRate : Action
        object EditCategories : Action
        object ShowAllCategories : Action
        object ShowAllCurrencies : Action
    }

    data class State(
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val categories: List<TransactionEditCategory> = emptyList(),
        val selectedCategory: TransactionEditCategory? = null,
        val currencies: List<TransactionEditCurrency> = emptyList(),
        val selectedCurrency: TransactionEditCurrency? = null,
        val amount: String = "",
        val rate: String = "",
        val rateAuto: Boolean = true,
        val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
        val convertedAmountText: String = "",
        val accountCurrencyName: String = "",
        val accountCurrencySymbol: String = "",
        val txCurrencySymbol: String = "",
        val showRate: Boolean = false,
        val date: LocalDateTime? = null,
    )
}
