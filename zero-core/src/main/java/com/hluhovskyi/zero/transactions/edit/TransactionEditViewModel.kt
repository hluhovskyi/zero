package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Account
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.Category
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.flow.Flow

interface TransactionEditViewModel
    : AttachableStateViewModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: Account) : Action
        data class SelectCurrency(val currency: Currency) : Action
        data class SelectCategory(val category: Category): Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeRate(val rate: String) : Action
        object Save : Action
        object EditCategories : Action
    }

    data class State(
        val selectedAccount: Account? = null,
        val accounts: List<Account> = emptyList(),
        val selectedCurrency: Currency? = null,
        val currencies: List<Currency> = emptyList(),
        val selectedCategory: Category? = null,
        val categories: List<Category> = emptyList(),
        val amount: String = "",
        val rate: String = "",
    )
}