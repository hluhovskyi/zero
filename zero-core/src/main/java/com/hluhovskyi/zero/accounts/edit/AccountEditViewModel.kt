package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.StateViewModel

interface AccountEditViewModel
    : AttachableStateViewModel<AccountEditViewModel.Action, AccountEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String): Action
        data class SelectCurrency(val currency: Currency): Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val currencies: List<Currency> = emptyList(),
        val selectedCurrency: Currency? = null
    )
}