package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel
import kotlinx.datetime.LocalDateTime

interface TransactionEditViewModel
    : ActionStateModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object Save : Action
        object Discard : Action
    }

    data class State(
        val transactionTypes: List<TransactionEditType> = emptyList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val date: LocalDateTime = LocalDateTime(1970, 1, 1, 0, 0, 0),
        val selectedCategory: TransactionEditCategory? = null,
    )
    }