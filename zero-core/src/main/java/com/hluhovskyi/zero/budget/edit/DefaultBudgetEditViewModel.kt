package com.hluhovskyi.zero.budget.edit

import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Id
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
import kotlinx.datetime.minus

internal class DefaultBudgetEditViewModel(
    private val categoryId: Id.Known,
    private val periodStart: LocalDate,
    private val periodEnd: LocalDate,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val budgetRepository: BudgetRepository,
    private val onBudgetSavedHandler: OnBudgetSavedHandler,
    private val onBackHandler: OnBackHandler,
    dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    BudgetEditViewModel {

    private val mutableState = MutableStateFlow(BudgetEditViewModel.State())
    override val state: Flow<BudgetEditViewModel.State> = mutableState

    private val prevPeriodStart = periodStart.minus(1, DateTimeUnit.MONTH)
    private val prevPeriodEnd = periodEnd.minus(1, DateTimeUnit.MONTH)

    private var initialSeedDone = false

    override fun perform(action: BudgetEditViewModel.Action) {
        when (action) {
            is BudgetEditViewModel.Action.ChangeAmount -> {
                val prevString = previousAmountString()
                mutableState.update {
                    it.copy(
                        amountText = action.text,
                        isPreviousSelected = prevString != null && action.text == prevString,
                    )
                }
            }
            BudgetEditViewModel.Action.TapPreviousChip -> {
                val prevString = previousAmountString() ?: "0"
                mutableState.update { it.copy(amountText = prevString, isPreviousSelected = true) }
            }
            BudgetEditViewModel.Action.TapSave -> scope.launch {
                val state = mutableState.value
                val parsed = state.amountText.toBigDecimalOrNull() ?: return@launch
                val amount = Amount(parsed)
                budgetRepository.insert(
                    BudgetRepository.BudgetInsert(
                        categoryId = categoryId,
                        type = BudgetType.EXPENSE,
                        amount = amount,
                        periodStart = periodStart,
                        periodEnd = periodEnd,
                    ),
                )
                onBudgetSavedHandler.onSaved(state.categoryName, amount)
                onBackHandler.onBack()
            }
            BudgetEditViewModel.Action.TapClose -> onBackHandler.onBack()
        }
    }

    override fun attachOnMain() {
        scope.launch {
            combine(
                categoriesQueryUseCase.queryById(categoryId),
                budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, periodStart, periodEnd)),
                budgetRepository.query(BudgetRepository.Criteria.ForCategoryAndPeriod(categoryId, prevPeriodStart, prevPeriodEnd)),
            ) { category, currentBudget, prevBudget ->
                Triple(category, currentBudget, prevBudget)
            }.collectLatest { (category, currentBudget, prevBudget) ->
                mutableState.update { state ->
                    val seededAmountText = if (!initialSeedDone) {
                        initialSeedDone = true
                        currentBudget?.amount?.value?.stripTrailingZeros()?.toPlainString() ?: "0"
                    } else {
                        state.amountText
                    }
                    state.copy(
                        categoryName = category.name,
                        icon = category.icon,
                        colorScheme = category.colorScheme,
                        amountText = seededAmountText,
                        isEditing = currentBudget != null,
                        previousPeriodAmount = prevBudget?.amount,
                    )
                }
            }
        }
    }

    private fun previousAmountString(): String? = mutableState.value.previousPeriodAmount?.value?.stripTrailingZeros()?.toPlainString()
}
