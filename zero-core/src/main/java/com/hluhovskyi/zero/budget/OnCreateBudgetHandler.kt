package com.hluhovskyi.zero.budget

import kotlinx.datetime.LocalDate

fun interface OnCreateBudgetHandler {
    fun onCreate(periodStart: LocalDate, periodEnd: LocalDate)

    companion object {
        val Noop = OnCreateBudgetHandler { _, _ -> }
    }
}
