package com.hluhovskyi.zero.budget.bulksetup

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface BudgetBulkSetupViewModel : AttachableActionStateModel<BudgetBulkSetupViewModel.Action, BudgetBulkSetupViewModel.State> {

    sealed interface Action {
        object TapCopyAll : Action
        data class StartEdit(val categoryId: Id.Known) : Action
        data class ChangeEditAmount(val text: String) : Action
        object TapPreviousChip : Action
        object CommitEdit : Action
        object DismissEdit : Action
        object TapCreate : Action
        object TapClose : Action
    }

    data class State(
        val periodLabel: String = "",
        val categories: List<CategoryRow> = emptyList(),
        val previousPeriodTotal: Amount = Amount.zero(),
        val previousPeriodCount: Int = 0,
        val editingCategoryId: Id.Known? = null,
        val editingAmountText: String = "0",
        val isLoading: Boolean = true,
    ) {
        val setCount: Int
            get() = categories.count { it.amount > Amount.zero() }

        val totalBudgeted: Amount
            get() = categories.fold(Amount.zero()) { acc, row -> acc + row.amount }
    }

    data class CategoryRow(
        val categoryId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount = Amount.zero(),
        val previousAmount: Amount? = null,
    )
}
