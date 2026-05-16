package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id

interface BudgetViewModel : AttachableActionStateModel<BudgetViewModel.Action, BudgetViewModel.State> {

    sealed interface Action {
        object SelectOlderMonth : Action
        object SelectNewerMonth : Action
        data class TapCategory(val categoryId: Id.Known) : Action
        object TapCopyFromPrevious : Action
        object ConfirmCopy : Action
        object CancelCopy : Action
        data class ChangeEditAmount(val text: String) : Action
        object TapPreviousChip : Action
        object CommitInlineEdit : Action
        object DismissInlineEdit : Action
    }

    data class State(
        val monthOffset: Int = 0,
        val displayedPeriodLabel: String = "",
        val previousPeriodLabel: String = "",
        val hasPrevious: Boolean = true,
        val hasNext: Boolean = true,
        val budgeted: List<BudgetQueryUseCase.Budgeted> = emptyList(),
        val previousPeriodBudgets: List<BudgetQueryUseCase.Budgeted> = emptyList(),
        val isLoading: Boolean = true,
        val copyConfirmVisible: Boolean = false,
        val editingCategoryId: Id.Known? = null,
        val editingAmountText: String = "0",
        val skippedInSession: Set<Id.Known> = emptySet(),
    ) {
        val editingPreviousAmount: Amount?
            get() = previousPeriodBudgets
                .firstOrNull { it.categoryId == editingCategoryId && it.budgetId != null }
                ?.budgeted

        val isPreviousAmountSelected: Boolean
            get() = editingPreviousAmount
                ?.value
                ?.stripTrailingZeros()
                ?.toPlainString() == editingAmountText
    }
}
