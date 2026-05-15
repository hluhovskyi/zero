package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
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
    private val bulkBudgetSaveUseCase: BulkBudgetSaveUseCase,
    private val budgetToastUseCase: BudgetToastUseCase,
    private val budgetRepository: BudgetRepository,
    @Suppress("unused") private val onCategoryTappedHandler: OnCategoryTappedHandler,
    private val onCreateBudgetHandler: OnCreateBudgetHandler,
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
            BudgetViewModel.Action.TapCreateBudget -> scope.launch {
                val (currentStart, currentEnd) = currentPeriod()
                onCreateBudgetHandler.onCreate(currentStart, currentEnd)
            }
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
            BudgetViewModel.Action.ToastShown -> {
                mutableState.update { it.copy(toastMessage = null) }
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
                    )
                }
            }
            is BudgetViewModel.Action.ChangeEditAmount -> {
                mutableState.update { it.copy(editingAmountText = action.text) }
            }
            BudgetViewModel.Action.TapPreviousChip -> {
                val state = mutableState.value
                val editingId = state.editingCategoryId ?: return
                val prevRow = state.previousPeriodBudgets.firstOrNull { it.categoryId == editingId && it.budgetId != null }
                val prevText = prevRow?.budgeted?.value?.stripTrailingZeros()?.toPlainString() ?: "0"
                mutableState.update { it.copy(editingAmountText = prevText) }
            }
            BudgetViewModel.Action.DismissInlineEdit -> {
                mutableState.update { it.copy(editingCategoryId = null, editingAmountText = "0") }
            }
            BudgetViewModel.Action.CommitInlineEdit -> commitInlineEdit()
        }
    }

    private fun commitInlineEdit() = scope.launch {
        val state = mutableState.value
        val editingId = state.editingCategoryId ?: return@launch
        val parsed = state.editingAmountText.toBigDecimalOrNull()
        val amount = if (parsed != null) Amount(parsed) else Amount.zero()
        if (amount > Amount.zero()) {
            val (currentStart, currentEnd) = currentPeriod()
            val existing = state.budgeted.firstOrNull { it.categoryId == editingId }
            budgetRepository.insert(
                BudgetRepository.BudgetInsert(
                    id = existing?.budgetId ?: Id.Unknown,
                    categoryId = editingId,
                    type = BudgetType.EXPENSE,
                    amount = amount,
                    periodStart = currentStart,
                    periodEnd = currentEnd,
                ),
            )
        }
        val nextUnset = nextUnsetCategoryId(state, after = editingId)
        if (nextUnset != null) {
            mutableState.update {
                it.copy(editingCategoryId = nextUnset, editingAmountText = "0")
            }
        } else {
            mutableState.update {
                it.copy(editingCategoryId = null, editingAmountText = "0")
            }
        }
    }

    private fun nextUnsetCategoryId(state: BudgetViewModel.State, after: Id.Known): Id.Known? {
        val rows = state.budgeted
        val currentIndex = rows.indexOfFirst { it.categoryId == after }
        if (currentIndex < 0) return null
        val tail = rows.drop(currentIndex + 1)
        val head = rows.take(currentIndex)
        return (tail + head).firstOrNull { it.budgetId == null && it.categoryId != after }?.categoryId
    }

    private fun performCopy() = scope.launch {
        val state = mutableState.value
        val (currentStart, currentEnd) = currentPeriod()
        val entries = state.previousPeriodBudgets
            .filter { it.budgetId != null }
            .map { BulkBudgetSaveUseCase.Entry(categoryId = it.categoryId, amount = it.budgeted) }
        if (entries.isEmpty()) return@launch
        bulkBudgetSaveUseCase.save(currentStart, currentEnd, BudgetType.EXPENSE, entries)
        budgetToastUseCase.show("Copied ${entries.size} categories from ${state.previousPeriodLabel}")
    }

    private fun currentPeriod(): Pair<LocalDate, LocalDate> {
        val today = periodResolver.today()
        return periodResolver.monthOffsetFrom(today, monthOffset.value)
    }

    override fun attachOnMain() {
        observeToasts()
        observePeriods()
    }

    private fun observeToasts() {
        scope.launch {
            budgetToastUseCase.messages.collectLatest { message ->
                mutableState.update { it.copy(toastMessage = message) }
            }
        }
    }

    private fun observePeriods() {
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
