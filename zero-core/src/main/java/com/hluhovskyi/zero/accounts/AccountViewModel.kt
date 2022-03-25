package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface AccountViewModel
    : AttachableActionStateModel<AccountViewModel.Action, AccountViewModel.State> {

    sealed interface Action {

    }

    data class State(
        val accounts: List<Account> = emptyList()
    )
}