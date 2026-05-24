package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.budget.over.BudgetOverViewModel
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate

fun interface OnOverActionTappedHandler {
    fun onTap(
        categoryId: Id.Known,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        initialMode: BudgetOverViewModel.Mode?,
    )

    companion object {
        val Noop = OnOverActionTappedHandler { _, _, _, _ -> }
    }
}
