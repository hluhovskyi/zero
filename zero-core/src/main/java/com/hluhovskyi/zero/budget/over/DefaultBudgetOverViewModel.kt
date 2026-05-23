package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.BudgetType
import com.hluhovskyi.zero.budget.toInsert
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

internal class DefaultBudgetOverViewModel(
    private val categoryId: Id.Known,
    private val periodStart: LocalDate,
    private val periodEnd: LocalDate,
    private val initialMode: BudgetOverViewModel.Mode?,
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val budgetRepository: BudgetRepository,
    private val onReallocateCompletedHandler: OnReallocateCompletedHandler,
    private val onIncreaseCompletedHandler: OnIncreaseCompletedHandler,
    private val onBackHandler: OnBackHandler,
    dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    BudgetOverViewModel {

    private val mutableState = MutableStateFlow(
        BudgetOverViewModel.State(mode = initialMode ?: BudgetOverViewModel.Mode.CHOICE),
    )
    override val state: Flow<BudgetOverViewModel.State> = mutableState

    private var initialSeedDone = false

    override fun perform(action: BudgetOverViewModel.Action) {
        when (action) {
            BudgetOverViewModel.Action.TapReallocateOption -> mutableState.update {
                it.copy(mode = BudgetOverViewModel.Mode.REALLOCATE)
            }
            BudgetOverViewModel.Action.TapIncreaseOption -> mutableState.update {
                it.copy(mode = BudgetOverViewModel.Mode.INCREASE)
            }
            BudgetOverViewModel.Action.TapBack -> {
                val current = mutableState.value
                if (initialMode == null && current.mode != BudgetOverViewModel.Mode.CHOICE) {
                    mutableState.update { it.copy(mode = BudgetOverViewModel.Mode.CHOICE) }
                } else {
                    onBackHandler.onBack()
                }
            }
            BudgetOverViewModel.Action.TapClose -> onBackHandler.onBack()
            is BudgetOverViewModel.Action.SelectSource -> mutableState.update { state ->
                state.withSelectedSource(action.id)
            }
            BudgetOverViewModel.Action.ConfirmReallocate -> confirmReallocate()
            is BudgetOverViewModel.Action.ChangeIncreaseAmount -> mutableState.update { state ->
                state.withIncreaseAmount(action.text)
            }
            is BudgetOverViewModel.Action.TapIncreaseSuggestion -> mutableState.update { state ->
                state.withIncreaseAmount(amountIntegerString(action.amount))
            }
            BudgetOverViewModel.Action.ConfirmIncrease -> confirmIncrease()
        }
    }

    override fun attachOnMain() {
        scope.launch {
            budgetQueryUseCase.query(periodStart, periodEnd).collectLatest { rows ->
                mutableState.update { current -> current.withRows(rows) }
            }
        }
    }

    private fun confirmReallocate() = scope.launch {
        val snapshot = mutableState.value
        val target = snapshot.target ?: return@launch
        val source = snapshot.selectedSource ?: return@launch
        val amountToMove = snapshot.amountToMove
        if (amountToMove <= Amount.zero()) return@launch
        val sourceBudget = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(source.categoryId, periodStart, periodEnd))
            .first() ?: return@launch
        val targetBudget = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(target.categoryId, periodStart, periodEnd))
            .first() ?: return@launch
        val newSourceAmount = (sourceBudget.amount - amountToMove).coerceAtLeastZero()
        val newTargetAmount = targetBudget.amount + amountToMove
        budgetRepository.insert(
            listOf(
                sourceBudget.toInsert().copy(amount = newSourceAmount),
                targetBudget.toInsert().copy(amount = newTargetAmount),
            ),
        )
        onReallocateCompletedHandler.onComplete(source.name, target.name, amountToMove)
        onBackHandler.onBack()
    }

    private fun confirmIncrease() = scope.launch {
        val snapshot = mutableState.value
        val target = snapshot.target ?: return@launch
        val delta = snapshot.increaseAmountText.toBigDecimalOrNull()?.let { Amount(it) } ?: return@launch
        if (delta <= Amount.zero()) return@launch
        val targetBudget = budgetRepository
            .query(BudgetRepository.Criteria.ForCategoryAndPeriod(target.categoryId, periodStart, periodEnd))
            .first() ?: return@launch
        val newAmount = targetBudget.amount + delta
        budgetRepository.insert(targetBudget.toInsert().copy(amount = newAmount))
        onIncreaseCompletedHandler.onComplete(target.name, newAmount)
        onBackHandler.onBack()
    }

    private fun BudgetOverViewModel.State.withRows(
        rows: List<BudgetQueryUseCase.Budgeted>,
    ): BudgetOverViewModel.State {
        val targetRow = rows.firstOrNull { it.categoryId == categoryId }
        if (targetRow == null || targetRow.budgetId == null || targetRow.spent <= targetRow.budgeted) {
            return copy(
                target = null,
                reallocationSources = emptyList(),
                selectedSource = null,
                amountToMove = Amount.zero(),
                increaseSuggestions = emptyList(),
            )
        }
        val overage = targetRow.spent - targetRow.budgeted
        val target = BudgetOverViewModel.TargetItem(
            categoryId = targetRow.categoryId,
            name = targetRow.categoryName,
            icon = targetRow.icon,
            colorScheme = targetRow.colorScheme,
            budgeted = targetRow.budgeted,
            spent = targetRow.spent,
            overage = overage,
        )
        val sources = rows
            .filter { it.categoryId != categoryId && it.budgetId != null && it.budgeted > it.spent }
            .map { row ->
                val remaining = row.budgeted - row.spent
                BudgetOverViewModel.SourceItem(
                    categoryId = row.categoryId,
                    name = row.categoryName,
                    icon = row.icon,
                    colorScheme = row.colorScheme,
                    budgeted = row.budgeted,
                    spent = row.spent,
                    remaining = remaining,
                    coversIt = remaining >= overage,
                )
            }
            .sortedByDescending { it.remaining.value }
        val previouslySelectedId = selectedSource?.categoryId
        val selectedSource = sources.firstOrNull { it.categoryId == previouslySelectedId }
        val sourcesWithSelection = sources.map { it.copy(selected = it.categoryId == selectedSource?.categoryId) }
        val amountToMove = selectedSource?.let { capAtOverage(it.remaining, overage) } ?: Amount.zero()
        val increaseText = if (!initialSeedDone) {
            initialSeedDone = true
            amountIntegerString(overage)
        } else {
            increaseAmountText
        }
        val suggestions = increaseSuggestionsFor(target.budgeted, overage, selectedAmountText = increaseText)
        val newBudgetedAfterIncrease = newBudgetedFor(target.budgeted, increaseText)
        return copy(
            target = target,
            reallocationSources = sourcesWithSelection,
            selectedSource = selectedSource,
            amountToMove = amountToMove,
            increaseAmountText = increaseText,
            increaseSuggestions = suggestions,
            newBudgetedAfterIncrease = newBudgetedAfterIncrease,
        )
    }

    private fun BudgetOverViewModel.State.withSelectedSource(
        id: Id.Known,
    ): BudgetOverViewModel.State {
        val target = target ?: return this
        val selected = reallocationSources.firstOrNull { it.categoryId == id } ?: return this
        val sourcesWithSelection = reallocationSources.map { it.copy(selected = it.categoryId == id) }
        return copy(
            reallocationSources = sourcesWithSelection,
            selectedSource = selected,
            amountToMove = capAtOverage(selected.remaining, target.overage),
        )
    }

    private fun BudgetOverViewModel.State.withIncreaseAmount(
        text: String,
    ): BudgetOverViewModel.State {
        val target = target ?: return copy(increaseAmountText = text)
        val suggestions = increaseSuggestions.map { it.copy(selected = amountIntegerString(it.amount) == text) }
        return copy(
            increaseAmountText = text,
            increaseSuggestions = suggestions,
            newBudgetedAfterIncrease = newBudgetedFor(target.budgeted, text),
        )
    }
}

private fun capAtOverage(remaining: Amount, overage: Amount): Amount =
    if (remaining > overage) overage else remaining

private fun Amount.coerceAtLeastZero(): Amount =
    if (this < Amount.zero()) Amount.zero() else this

private fun increaseSuggestionsFor(
    budgeted: Amount,
    overage: Amount,
    selectedAmountText: String,
): List<BudgetOverViewModel.IncreaseSuggestion> {
    val budgetedBd = budgeted.value
    val overageBd = overage.value
    if (overageBd <= BigDecimal.ZERO) return emptyList()
    val exact = overageBd.setScale(0, RoundingMode.UP)
    val tenWithBuffer = overageBd
        .divide(BigDecimal.TEN, 0, RoundingMode.UP)
        .multiply(BigDecimal.TEN) + BigDecimal.TEN
    val totalRoundedToNext50 = budgetedBd.add(overageBd)
        .divide(BigDecimal(50), 0, RoundingMode.UP)
        .multiply(BigDecimal(50))
        .subtract(budgetedBd)
    return listOf(exact, tenWithBuffer, totalRoundedToNext50)
        .map { it.setScale(0, RoundingMode.UP) }
        .filter { it > BigDecimal.ZERO }
        .distinct()
        .take(3)
        .map { bd ->
            val amount = Amount(bd)
            BudgetOverViewModel.IncreaseSuggestion(
                amount = amount,
                newBudgeted = budgeted + amount,
                selected = amountIntegerString(amount) == selectedAmountText,
            )
        }
}

private fun newBudgetedFor(budgeted: Amount, text: String): Amount {
    val parsed = text.toBigDecimalOrNull() ?: return budgeted
    return budgeted + Amount(parsed)
}

private fun amountIntegerString(amount: Amount): String =
    amount.value.setScale(0, RoundingMode.UP).toPlainString()
