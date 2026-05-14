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
        object Duplicate : Action
    }

    data class State(
        val transactionTypes: List<TransactionEditType> = TransactionEditType.values().toList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val date: LocalDateTime? = null,
        val selectedCategory: TransactionEditCategory? = null,
        val headerMode: HeaderMode = HeaderMode.New,
        val notes: String = "",
    )

    sealed interface HeaderMode {
        object New : HeaderMode
        object Edit : HeaderMode
        data class DuplicateFrom(val subtitle: String) : HeaderMode
    }
}
