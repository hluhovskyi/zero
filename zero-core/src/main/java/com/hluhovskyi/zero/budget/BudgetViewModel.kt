package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id

interface BudgetViewModel : AttachableActionStateModel<BudgetViewModel.Action, BudgetViewModel.State> {

    sealed interface Action {
        object SelectOlderMonth : Action
        object SelectNewerMonth : Action
        data class TapCategory(val categoryId: Id.Known) : Action
        object TapCreateBudget : Action
        object TapCopyFromPrevious : Action
        object ConfirmCopy : Action
        object CancelCopy : Action
        object ToastShown : Action
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
        val toastMessage: String? = null,
        val copyConfirmVisible: Boolean = false,
        val editingCategoryId: Id.Known? = null,
        val editingAmountText: String = "0",
    )
}
