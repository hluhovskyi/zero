package com.hluhovskyi.zero.budget.bulksetup

import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.budget.BulkBudgetSaveUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus

internal class DefaultBudgetBulkSetupViewModel(
    private val periodStart: LocalDate,
    private val periodEnd: LocalDate,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val bulkBudgetSaveUseCase: BulkBudgetSaveUseCase,
    private val onBulkSavedHandler: OnBulkBudgetSavedHandler,
    private val onBackHandler: OnBackHandler,
    dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    BudgetBulkSetupViewModel {

    private val mutableState = MutableStateFlow(BudgetBulkSetupViewModel.State())
    override val state: Flow<BudgetBulkSetupViewModel.State> = mutableState

    private val prevPeriodStart = periodStart.minus(1, DateTimeUnit.MONTH)
    private val prevPeriodEnd = periodEnd.minus(1, DateTimeUnit.MONTH)

    private var initialSeedDone = false

    override fun perform(action: BudgetBulkSetupViewModel.Action) {
        when (action) {
            BudgetBulkSetupViewModel.Action.TapCopyAll -> {
                mutableState.update { current ->
                    current.copy(
                        categories = current.categories.map { row ->
                            row.copy(amount = row.previousAmount ?: row.amount)
                        },
                    )
                }
            }
            is BudgetBulkSetupViewModel.Action.StartEdit -> {
                val row = mutableState.value.categories.firstOrNull { it.categoryId == action.categoryId }
                val text = row?.amount?.value?.stripTrailingZeros()?.toPlainString() ?: "0"
                mutableState.update {
                    it.copy(
                        editingCategoryId = action.categoryId,
                        editingAmountText = text,
                    )
                }
            }
            is BudgetBulkSetupViewModel.Action.ChangeEditAmount -> {
                mutableState.update { it.copy(editingAmountText = action.text) }
            }
            BudgetBulkSetupViewModel.Action.TapPreviousChip -> {
                val state = mutableState.value
                val editingRow = state.categories.firstOrNull { it.categoryId == state.editingCategoryId }
                val prevText = editingRow?.previousAmount?.value?.stripTrailingZeros()?.toPlainString() ?: "0"
                mutableState.update { it.copy(editingAmountText = prevText) }
            }
            BudgetBulkSetupViewModel.Action.CommitEdit -> commitEdit()
            BudgetBulkSetupViewModel.Action.DismissEdit -> {
                mutableState.update { it.copy(editingCategoryId = null, editingAmountText = "0") }
            }
            BudgetBulkSetupViewModel.Action.TapCreate -> scope.launch {
                val state = mutableState.value
                val entries = state.categories
                    .filter { it.amount > Amount.zero() }
                    .map { BulkBudgetSaveUseCase.Entry(categoryId = it.categoryId, amount = it.amount) }
                bulkBudgetSaveUseCase.save(periodStart, periodEnd, BudgetType.EXPENSE, entries)
                onBulkSavedHandler.onSaved(state.setCount, state.totalBudgeted)
                onBackHandler.onBack()
            }
            BudgetBulkSetupViewModel.Action.TapClose -> onBackHandler.onBack()
        }
    }

    private fun commitEdit() {
        val state = mutableState.value
        val editingId = state.editingCategoryId ?: return
        val parsed = state.editingAmountText.toBigDecimalOrNull()
        val amount = if (parsed != null) Amount(parsed) else Amount.zero()
        mutableState.update { current ->
            current.copy(
                categories = current.categories.map { row ->
                    if (row.categoryId == editingId) row.copy(amount = amount) else row
                },
                editingCategoryId = null,
                editingAmountText = "0",
            )
        }
    }

    override fun attachOnMain() {
        scope.launch {
            combine(
                budgetQueryUseCase.query(periodStart, periodEnd),
                budgetQueryUseCase.query(prevPeriodStart, prevPeriodEnd),
            ) { current, previous -> current to previous }
                .collectLatest { (current, previous) ->
                    val prevByCategory = previous.associateBy { it.categoryId }
                    val rows = current.map { row ->
                        val prevAmount = prevByCategory[row.categoryId]
                            ?.takeIf { it.budgetId != null }
                            ?.budgeted
                        val seedAmount = if (!initialSeedDone) row.budgeted else null
                        val existingRow = mutableState.value.categories.firstOrNull { it.categoryId == row.categoryId }
                        BudgetBulkSetupViewModel.CategoryRow(
                            categoryId = row.categoryId,
                            name = row.categoryName,
                            icon = row.icon,
                            colorScheme = row.colorScheme,
                            amount = seedAmount ?: existingRow?.amount ?: Amount.zero(),
                            previousAmount = prevAmount,
                        )
                    }
                    val prevWithBudgets = previous.filter { it.budgetId != null }
                    val prevTotal = prevWithBudgets.fold(Amount.zero()) { acc, b -> acc + b.budgeted }
                    initialSeedDone = true
                    mutableState.update {
                        it.copy(
                            periodLabel = label(periodStart),
                            categories = rows,
                            previousPeriodTotal = prevTotal,
                            previousPeriodCount = prevWithBudgets.size,
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
