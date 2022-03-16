package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.StateViewModel

interface TransactionEditViewModel
    : StateViewModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action
        object Save : Action
    }

    data class State(
        val transactionType: TransactionEditType = TransactionEditType.EXPENSE
    )
}