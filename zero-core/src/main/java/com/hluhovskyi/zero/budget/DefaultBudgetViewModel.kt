package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.budget.over.BudgetOverViewModel
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal class DefaultBudgetViewModel(
    private val budgetUseCase: BudgetUseCase,
    @Suppress("unused") private val onCategoryTappedHandler: OnCategoryTappedHandler,
    private val onOverActionTappedHandler: OnOverActionTappedHandler,
    dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    BudgetViewModel {

    private val mutableState = MutableStateFlow(BudgetViewModel.State())
    override val state: Flow<BudgetViewModel.State> = mutableState

    private val monthOffset = MutableStateFlow(0)

    override fun perform(action: BudgetViewModel.Action) {
        when (action) {
            BudgetViewModel.Action.SelectOlderMonth -> monthOffset.update { it - 1 }
            BudgetViewModel.Action.SelectNewerMonth -> monthOffset.update { it + 1 }
            BudgetViewModel.Action.TapCopyFromPrevious -> {
                val current = mutableState.value
                if (current.budgeted.none { it.budgetId != null }) {
                    performCopy()
                } else {
                    mutableState.update { it.copy(copyConfirmVisible = true) }
                }
            }
            BudgetViewModel.Action.ConfirmCopy -> {
                mutableState.update { it.copy(copyConfirmVisible = false) }
                performCopy()
            }
            BudgetViewModel.Action.CancelCopy -> {
                mutableState.update { it.copy(copyConfirmVisible = false) }
            }
            is BudgetViewModel.Action.TapCategory -> {
                mutableState.update { state ->
                    val row = state.budgeted.firstOrNull { it.categoryId == action.categoryId }
                    state.copy(
                        editingCategoryId = action.categoryId,
                        editingAmountText = if (row != null && row.budgetId != null) {
                            row.budgeted.value.stripTrailingZeros().toPlainString()
                        } else {
                            "0"
                        },
                        skippedInSession = emptySet(),
                    )
                }
            }
            is BudgetViewModel.Action.TapReallocate -> dispatchOverAction(
                action.categoryId,
                BudgetOverViewModel.Mode.REALLOCATE,
            )
            is BudgetViewModel.Action.TapIncrease -> dispatchOverAction(
                action.categoryId,
                BudgetOverViewModel.Mode.INCREASE,
            )
            is BudgetViewModel.Action.ChangeEditAmount -> {
                mutableState.update { it.copy(editingAmountText = action.text) }
            }
            BudgetViewModel.Action.TapPreviousChip -> {
                mutableState.update { state ->
                    state.copy(
                        editingAmountText = state.editingPreviousAmount
                            ?.value
                            ?.stripTrailingZeros()
                            ?.toPlainString() ?: "0",
                    )
                }
            }
            BudgetViewModel.Action.DismissInlineEdit -> {
                mutableState.update {
                    it.copy(
                        editingCategoryId = null,
                        editingAmountText = "0",
                        skippedInSession = emptySet(),
                    )
                }
            }
            BudgetViewModel.Action.CommitInlineEdit -> commitInlineEdit()
        }
    }

    private fun commitInlineEdit() = scope.launch {
        val state = mutableState.value
        val editingId = state.editingCategoryId ?: return@launch
        val parsed = state.editingAmountText.toBigDecimalOrNull()
        val amount = if (parsed != null) Amount(parsed) else Amount.zero()
        val saved = amount > Amount.zero()
        if (saved) {
            budgetUseCase.save(
                monthOffset = monthOffset.value,
                type = BudgetType.EXPENSE,
                categoryId = editingId,
                amount = amount,
            )
        }
        val skipped = if (saved) state.skippedInSession else state.skippedInSession + editingId
        val nextUnset = nextUnsetCategoryId(state, after = editingId, skipped = skipped)
        // Respect a concurrent DismissInlineEdit: if the user already closed the numpad or
        // moved to a different category while save was suspending, don't stomp their action.
        mutableState.update { current ->
            if (current.editingCategoryId != editingId) {
                current
            } else if (nextUnset != null) {
                current.copy(
                    editingCategoryId = nextUnset,
                    editingAmountText = "0",
                    skippedInSession = skipped,
                )
            } else {
                current.copy(
                    editingCategoryId = null,
                    editingAmountText = "0",
                    skippedInSession = emptySet(),
                )
            }
        }
    }

    private fun nextUnsetCategoryId(
        state: BudgetViewModel.State,
        after: Id.Known,
        skipped: Set<Id.Known>,
    ): Id.Known? {
        val rows = state.budgeted
        val currentIndex = rows.indexOfFirst { it.categoryId == after }
        if (currentIndex < 0) return null
        val tail = rows.drop(currentIndex + 1)
        val head = rows.take(currentIndex)
        return (tail + head)
            .firstOrNull { it.budgetId == null && it.categoryId != after && it.categoryId !in skipped }
            ?.categoryId
    }

    private fun performCopy() = scope.launch {
        budgetUseCase.replaceFromPrevious(monthOffset.value, BudgetType.EXPENSE)
    }

    private fun dispatchOverAction(
        categoryId: Id.Known,
        mode: BudgetOverViewModel.Mode,
    ) {
        val snapshot = mutableState.value
        val start = snapshot.currentPeriodStart ?: return
        val end = snapshot.currentPeriodEnd ?: return
        onOverActionTappedHandler.onTap(categoryId, start, end, mode)
    }

    override fun attachOnMain() {
        scope.launch {
            budgetUseCase.observe(monthOffset, BudgetType.EXPENSE).collectLatest { state ->
                mutableState.update {
                    it.copy(
                        displayedPeriodLabel = label(state.currentPeriod.start),
                        previousPeriodLabel = label(state.previousPeriod.start),
                        currentPeriodStart = state.currentPeriod.start,
                        currentPeriodEnd = state.currentPeriod.end,
                        budgeted = state.current,
                        previousPeriodBudgets = state.previous,
                        items = state.current.toItems(previousPeriod = state.previous),
                        summary = state.summary,
                        hasAnyBudget = state.hasAnyBudget,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun List<BudgetQueryUseCase.Budgeted>.toItems(
        previousPeriod: List<BudgetQueryUseCase.Budgeted>,
    ): List<BudgetViewModel.Item> {
        val previousById = previousPeriod
            .filter { it.budgetId != null }
            .associate { it.categoryId to it.budgeted }
        return map { row ->
            if (row.budgetId != null) {
                row.toSetItem()
            } else {
                row.toUnsetItem(previousAmount = previousById[row.categoryId])
            }
        }
    }

    private fun BudgetQueryUseCase.Budgeted.toSetItem(): BudgetViewModel.Item.Set {
        val rawProgress = if (budgeted > Amount.zero()) {
            (spent.value.toDouble() / budgeted.value.toDouble()).toFloat()
        } else {
            0f
        }
        val isOver = spent > budgeted
        val status = when {
            isOver -> BudgetViewModel.Item.Status.Over
            rawProgress > 0.85f -> BudgetViewModel.Item.Status.AlmostThere
            rawProgress > 0.65f -> BudgetViewModel.Item.Status.Watch
            else -> BudgetViewModel.Item.Status.Healthy
        }
        val remaining = if (isOver) spent - budgeted else budgeted - spent
        return BudgetViewModel.Item.Set(
            categoryId = categoryId,
            name = categoryName,
            icon = icon,
            colorScheme = colorScheme,
            spent = spent,
            budgeted = budgeted,
            remaining = remaining,
            progress = rawProgress.coerceIn(0f, 1f),
            status = status,
        )
    }

    private fun BudgetQueryUseCase.Budgeted.toUnsetItem(
        previousAmount: Amount?,
    ): BudgetViewModel.Item.Unset = BudgetViewModel.Item.Unset(
        categoryId = categoryId,
        name = categoryName,
        icon = icon,
        colorScheme = colorScheme,
        previousAmount = previousAmount,
    )

    private fun label(date: LocalDate): String = "${monthName(date.month)} ${date.year}"

    private fun monthName(month: Month): String = month.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}
