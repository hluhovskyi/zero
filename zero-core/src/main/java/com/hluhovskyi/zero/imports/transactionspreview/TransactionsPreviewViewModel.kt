package com.hluhovskyi.zero.imports.transactionspreview

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Image

interface TransactionsPreviewViewModel : ActionStateModel<TransactionsPreviewViewModel.Action, TransactionsPreviewViewModel.State> {

    data class DisplayTransaction(
        val id: String,
        val primaryText: String?,
        val amount: String,
        val accountName: String,
        val date: String,
        val colorScheme: ColorScheme?,
        val icon: Image?,
        val type: Type,
    ) {
        enum class Type { EXPENSE, INCOME, TRANSFER }
    }

    data class DateGroup(
        val dateLabel: String,
        val transactions: List<DisplayTransaction>,
    )

    data class State(
        val groups: List<DateGroup> = emptyList(),
        val totalCount: Int = 0,
    )

    sealed interface Action {
        object Confirm : Action
        object Back : Action
    }
}
