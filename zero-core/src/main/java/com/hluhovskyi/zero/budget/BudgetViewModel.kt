package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDate

interface BudgetViewModel : AttachableActionStateModel<BudgetViewModel.Action, BudgetViewModel.State> {

    sealed interface Action {
        object SelectOlderMonth : Action
        object SelectNewerMonth : Action
        data class TapCategory(val categoryId: Id.Known) : Action
        data class TapReallocate(val categoryId: Id.Known) : Action
        data class TapIncrease(val categoryId: Id.Known) : Action
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
        val currentPeriodStart: LocalDate? = null,
        val currentPeriodEnd: LocalDate? = null,
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
        /** Count of set budgets in the previous period — surfaced as "N budgets last month". */
        val previousBudgetSetCount: Int = previousPeriodBudgets.count { it.budgetId != null }

        /** True when the previous period has at least one set budget — gates copy-from-previous. */
        val hasAnyPreviousBudget: Boolean = previousBudgetSetCount > 0

        /** The currently-edited row, if any — view-side lookup by `editingCategoryId` lives here. */
        val editingRow: BudgetQueryUseCase.Budgeted? = editingCategoryId?.let { id ->
            budgeted.firstOrNull { it.categoryId == id }
        }

        /** True when there's still an unset category to advance to from the inline numpad. */
        val hasNextUnsetForEditing: Boolean = editingCategoryId?.let { id ->
            budgeted.any {
                it.categoryId != id &&
                    it.budgetId == null &&
                    it.categoryId !in skippedInSession
            }
        } ?: false

        val editingPreviousAmount: Amount? = previousPeriodBudgets
            .firstOrNull { it.categoryId == editingCategoryId && it.budgetId != null }
            ?.budgeted

        val isPreviousAmountSelected: Boolean = editingPreviousAmount
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
