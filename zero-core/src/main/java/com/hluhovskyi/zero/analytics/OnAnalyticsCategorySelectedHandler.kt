package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Id

fun interface OnAnalyticsCategorySelectedHandler {
    fun onSelected(categoryId: Id.Known)

    companion object {
        val Noop = OnAnalyticsCategorySelectedHandler { }
    }
}
