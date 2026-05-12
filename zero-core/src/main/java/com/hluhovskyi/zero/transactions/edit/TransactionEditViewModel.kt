package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel
import kotlinx.datetime.LocalDateTime

interface TransactionEditViewModel : ActionStateModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        data class ChangeNotes(val notes: String) : Action
        object Save : Action
        object Discard : Action
        object Delete : Action
    }

    data class State(
        val transactionTypes: List<TransactionEditType> = emptyList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val date: LocalDateTime? = null,
        val selectedCategory: TransactionEditCategory? = null,
        val isEditMode: Boolean = false,
        val notes: String = "",
    )
}
