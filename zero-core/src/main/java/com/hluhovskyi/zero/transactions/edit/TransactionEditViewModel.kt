package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ActionStateModel
import java.time.LocalDateTime

interface TransactionEditViewModel
    : ActionStateModel<TransactionEditViewModel.Action, TransactionEditViewModel.State> {

    sealed interface Action {
        data class ChangeTransactionType(val type: TransactionEditType) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object Save : Action
        object Discard : Action
        object ShowAllCategories : Action
        object DismissCategoryPicker : Action
    }

    data class State(
        val transactionTypes: List<TransactionEditType> = emptyList(),
        val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val date: LocalDateTime = LocalDateTime.now(),
        val selectedCategory: TransactionEditCategory? = null,
        val showCategoryPicker: Boolean = false,
    )
}