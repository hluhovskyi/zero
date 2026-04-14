// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewModel.kt
package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.common.ActionStateModel

interface TransactionsPreviewViewModel : ActionStateModel<TransactionsPreviewViewModel.Action, TransactionsPreviewViewModel.State> {

    data class DisplayTransaction(
        val primaryText: String,
        val amount: String,
        val accountName: String,
        val date: String,
        val type: Type,
    ) {
        enum class Type { EXPENSE, INCOME, TRANSFER }
    }

    data class State(
        val transactions: List<DisplayTransaction> = emptyList(),
        val totalCount: Int = 0,
    )

    sealed interface Action {
        object Confirm : Action
        object Back : Action
    }
}
