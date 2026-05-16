package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal class DefaultBudgetViewModel(
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val periodResolver: PeriodResolver,
    private val budgetSaveUseCase: BudgetSaveUseCase,
    private val bulkBudgetSaveUseCase: BulkBudgetSaveUseCase,
    @Suppress("unused") private val onCategoryTappedHandler: OnCategoryTappedHandler,
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
                val row = mutableState.value.budgeted.firstOrNull { it.categoryId == action.categoryId }
                val seedText = if (row != null && row.budgetId != null) {
                    row.budgeted.value.stripTrailingZeros().toPlainString()
                } else {
                    "0"
                }
                mutableState.update {
                    it.copy(
                        editingCategoryId = action.categoryId,
                        editingAmountText = seedText,
                        skippedInSession = emptySet(),
                    )
                }
            }
            is BudgetViewModel.Action.ChangeEditAmount -> {
                mutableState.update { it.copy(editingAmountText = action.text) }
            }
            BudgetViewModel.Action.TapPreviousChip -> {
                val state = mutableState.value
                val prevText = state.editingPreviousAmount?.value?.stripTrailingZeros()?.toPlainString() ?: "0"
                mutableState.update { it.copy(editingAmountText = prevText) }
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
            val existing = state.budgeted.firstOrNull { it.categoryId == editingId }
            budgetSaveUseCase.save(
                period = currentPeriod(),
                type = BudgetType.EXPENSE,
                categoryId = editingId,
                amount = amount,
                existingId = existing?.budgetId,
            )
        }
        val skipped = if (saved) state.skippedInSession else state.skippedInSession + editingId
        val nextUnset = nextUnsetCategoryId(state, after = editingId, skipped = skipped)
        if (nextUnset != null) {
            mutableState.update {
                it.copy(
                    editingCategoryId = nextUnset,
                    editingAmountText = "0",
                    skippedInSession = skipped,
                )
            }
        } else {
            mutableState.update {
                it.copy(
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
        val state = mutableState.value
        val entries = state.previousPeriodBudgets
            .filter { it.budgetId != null }
            .map { BulkBudgetSaveUseCase.Entry(categoryId = it.categoryId, amount = it.budgeted) }
        if (entries.isEmpty()) return@launch
        bulkBudgetSaveUseCase.save(currentPeriod(), BudgetType.EXPENSE, entries)
    }

    private fun currentPeriod(): DateRange {
        val today = periodResolver.today()
        val (start, end) = periodResolver.monthOffsetFrom(today, monthOffset.value)
        return DateRange(start, end)
    }

    override fun attachOnMain() {
        scope.launch {
            monthOffset
                .flatMapLatest { offset ->
                    val today = periodResolver.today()
                    val (currentStart, currentEnd) = periodResolver.monthOffsetFrom(today, offset)
                    val (previousStart, previousEnd) = periodResolver.monthOffsetFrom(today, offset - 1)
                    combine(
                        budgetQueryUseCase.query(currentStart, currentEnd),
                        budgetQueryUseCase.query(previousStart, previousEnd),
                    ) { current, previous ->
                        Triple(current, previous, Pair(currentStart, previousStart))
                    }
                }
                .collectLatest { (current, previous, periods) ->
                    val (currentStart, previousStart) = periods
                    mutableState.update {
                        it.copy(
                            displayedPeriodLabel = label(currentStart),
                            previousPeriodLabel = label(previousStart),
                            budgeted = current,
                            previousPeriodBudgets = previous,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun label(date: LocalDate): String = "${monthName(date.month)} ${date.year}"

    private fun monthName(month: Month): String = month.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}
