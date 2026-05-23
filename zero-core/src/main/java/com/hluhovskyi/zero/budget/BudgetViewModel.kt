package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

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
        val items: List<Item> = emptyList(),
        val summary: BudgetUseCase.Summary = BudgetUseCase.Summary.empty,
        val hasAnyBudget: Boolean = false,
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

    /**
     * View-shaped item consumed directly by the Budget list. The composable pattern-matches on
     * the variant — never inspects `budgetId`, never derives progress or status, never formats.
     */
    sealed interface Item {
        val categoryId: Id.Known
        val name: String
        val icon: Image
        val colorScheme: ColorScheme

        data class Set(
            override val categoryId: Id.Known,
            override val name: String,
            override val icon: Image,
            override val colorScheme: ColorScheme,
            val spent: Amount,
            val budgeted: Amount,
            /** Absolute remaining or overage amount (always non-negative). */
            val remaining: Amount,
            /** Spent fraction of budget, clipped to 0..1. */
            val progress: Float,
            val status: Status,
        ) : Item

        data class Unset(
            override val categoryId: Id.Known,
            override val name: String,
            override val icon: Image,
            override val colorScheme: ColorScheme,
            val previousAmount: Amount?,
        ) : Item

        enum class Status { Healthy, Watch, AlmostThere, Over }
    }
}
