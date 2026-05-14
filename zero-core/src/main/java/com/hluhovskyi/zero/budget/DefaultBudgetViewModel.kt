package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.BaseViewModel
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
            BudgetViewModel.Action.TapCreateBudget -> Unit
            BudgetViewModel.Action.TapCopyFromPrevious -> Unit
            is BudgetViewModel.Action.TapUnsetCategory -> Unit
            is BudgetViewModel.Action.TapSetCategory -> Unit
        }
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
