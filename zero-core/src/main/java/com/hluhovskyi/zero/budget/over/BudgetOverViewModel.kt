package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface BudgetOverViewModel : AttachableActionStateModel<BudgetOverViewModel.Action, BudgetOverViewModel.State> {

    enum class Mode { CHOICE, REALLOCATE, INCREASE }

    data class State(
        val mode: Mode = Mode.CHOICE,
        val target: TargetItem? = null,
        val reallocationSources: List<SourceItem> = emptyList(),
        val selectedSource: SourceItem? = null,
        /** Capped at the target's overage so we never move more than needed. */
        val amountToMove: Amount = Amount.zero(),
        val increaseAmountText: String = "0",
        val increaseSuggestions: List<IncreaseSuggestion> = emptyList(),
        val newBudgetedAfterIncrease: Amount = Amount.zero(),
    )

    data class TargetItem(
        val categoryId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val budgeted: Amount,
        val spent: Amount,
        /** spent - budgeted, always positive. */
        val overage: Amount,
    )

    data class SourceItem(
        val categoryId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val budgeted: Amount,
        val spent: Amount,
        /** budgeted - spent, always positive. */
        val remaining: Amount,
        /** True when [remaining] fully covers the target's overage. */
        val coversIt: Boolean,
        val selected: Boolean = false,
    )

    data class IncreaseSuggestion(
        val amount: Amount,
        /** target.budgeted + amount — what the new limit becomes if this is picked. */
        val newBudgeted: Amount,
        val selected: Boolean = false,
    )

    sealed interface Action {
        object TapReallocateOption : Action
        object TapIncreaseOption : Action
        object TapBack : Action
        object TapClose : Action
        data class SelectSource(val id: Id.Known) : Action
        object ConfirmReallocate : Action
        data class ChangeIncreaseAmount(val text: String) : Action
        data class TapIncreaseSuggestion(val amount: Amount) : Action
        object ConfirmIncrease : Action
    }
}
