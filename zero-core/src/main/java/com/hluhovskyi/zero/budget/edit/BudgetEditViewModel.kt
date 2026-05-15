package com.hluhovskyi.zero.budget.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image

interface BudgetEditViewModel : AttachableActionStateModel<BudgetEditViewModel.Action, BudgetEditViewModel.State> {

    data class State(
        val categoryName: String = "",
        val icon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme.Grey,
        val amountText: String = "0",
        val isEditing: Boolean = false,
        val previousPeriodAmount: Amount? = null,
        val isPreviousSelected: Boolean = false,
    )

    sealed interface Action {
        data class ChangeAmount(val text: String) : Action
        object TapPreviousChip : Action
        object TapSave : Action
        object TapClose : Action
    }
}
