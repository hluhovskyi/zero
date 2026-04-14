package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportAccount

interface AccountsReviewViewModel : ActionStateModel<AccountsReviewViewModel.Action, AccountsReviewViewModel.State> {

    data class State(val accounts: List<ImportAccount> = emptyList())

    sealed interface Action {
        object Next : Action
        object Back : Action
    }
}
