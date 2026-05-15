package com.hluhovskyi.zero.budget.edit

import com.hluhovskyi.zero.common.Amount

fun interface OnBudgetSavedHandler {
    fun onSaved(categoryName: String, amount: Amount)

    companion object {
        val Noop = OnBudgetSavedHandler { _, _ -> }
    }
}
