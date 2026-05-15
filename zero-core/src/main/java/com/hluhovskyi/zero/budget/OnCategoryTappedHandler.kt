package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate

fun interface OnCategoryTappedHandler {
    fun onTap(categoryId: Id.Known, periodStart: LocalDate, periodEnd: LocalDate)

    companion object {
        val Noop = OnCategoryTappedHandler { _, _, _ -> }
    }
}
