package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel

interface TransactionEditViewModel
    : ActionStateModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action
        object Save : Action
        object Discard : Action
    }

    data class State(
        val transactionTypes: List<TransactionEditType> = emptyList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
    )
}