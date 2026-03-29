package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Currency

interface AccountViewModel
    : AttachableActionStateModel<AccountViewModel.Action, AccountViewModel.State> {

    sealed interface Action {

    }

    data class State(
        val balance: Amount = Amount.zero(),
        val currency: Currency? = null,
        val accounts: List<Account> = emptyList(),
    )
}