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
    )
}
