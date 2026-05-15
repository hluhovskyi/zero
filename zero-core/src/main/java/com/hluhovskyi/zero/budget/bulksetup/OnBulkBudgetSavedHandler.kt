package com.hluhovskyi.zero.budget.bulksetup

import com.hluhovskyi.zero.common.Amount

fun interface OnBulkBudgetSavedHandler {
    fun onSaved(count: Int, total: Amount)

    companion object {
        val Noop = OnBulkBudgetSavedHandler { _, _ -> }
    }
}
