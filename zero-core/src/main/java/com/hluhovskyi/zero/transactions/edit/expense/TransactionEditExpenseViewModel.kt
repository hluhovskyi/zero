package com.hluhovskyi.zero.transactions.edit.expense

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import kotlinx.datetime.LocalDateTime

interface TransactionEditExpenseViewModel
    : ActionStateModel<TransactionEditExpenseViewModel.Action, TransactionEditExpenseViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectCurrency(val currency: TransactionEditCurrency) : Action
        data class SelectCategory(val category: TransactionEditCategory) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object EditCategories : Action
        object ShowAllCategories : Action
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
        val date: LocalDateTime = LocalDateTime(1970, 1, 1, 0, 0, 0),
    ) {

        val showRate: Boolean = if (selectedCurrency != null && selectedAccount != null) {
            selectedCurrency.id != selectedAccount.currencyId
        } else {
            false
        }
    }
}