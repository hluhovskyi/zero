package com.hluhovskyi.zero.imports.accounts

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id

interface ImportAccountPickerViewModel
    : AttachableActionStateModel<ImportAccountPickerViewModel.Action, ImportAccountPickerViewModel.State> {

    sealed interface Action {
        data class ChangeSelection(val item: AccountItem) : Action
        object Submit : Action
    }

    data class State(
        val items: List<AccountItem> = emptyList()
    )

    data class AccountItem(
        val id: Id.Known,
        val name: String,
        val selected: Boolean,
    )
}