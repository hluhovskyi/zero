package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.imports.ImportAccount
import com.hluhovskyi.zero.imports.ResolveStrategy

interface AccountsReviewViewModel : ActionStateModel<AccountsReviewViewModel.Action, AccountsReviewViewModel.State> {

    data class State(
        val accounts: List<ImportAccount> = emptyList(),
        val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
    ) {
        /** Total transaction count across all accounts up for review — used by the header copy. */
        val totalTransactions: Int = accounts.sumOf { it.transactionCount }
    }

    sealed interface Action {
        object Next : Action
        object Back : Action
        data class SetStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
    }
}
