package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.StateViewModel

interface AccountEditViewModel
    : AttachableStateViewModel<AccountEditViewModel.Action, AccountEditViewModel.State> {

    sealed interface Action {

    }

    data class State(
        val unit: Unit = Unit
    )
}