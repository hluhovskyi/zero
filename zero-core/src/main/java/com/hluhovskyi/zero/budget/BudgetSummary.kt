package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount

internal data class BudgetSummary(
    val totalBudgeted: Amount,
    val totalSpent: Amount,
    val overCount: Int,
    val overallPct: Float,
    val isOver: Boolean,
)

internal fun List<BudgetQueryUseCase.Budgeted>.toSummary(): BudgetSummary {
    val active = filter { it.budgetId != null }
    val totalBudgeted = active.fold(Amount.zero()) { acc, b -> acc + b.budgeted }
    val totalSpent = active.fold(Amount.zero()) { acc, b -> acc + b.spent }
    val overCount = active.count { it.spent > it.budgeted }
    val pct = if (totalBudgeted > Amount.zero()) {
        (totalSpent.value.toDouble() / totalBudgeted.value.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    return BudgetSummary(
        totalBudgeted = totalBudgeted,
        totalSpent = totalSpent,
        overCount = overCount,
        overallPct = pct,
        isOver = totalSpent > totalBudgeted,
    )
}
