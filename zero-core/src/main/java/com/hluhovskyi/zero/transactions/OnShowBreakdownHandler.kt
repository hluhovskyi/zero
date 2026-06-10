package com.hluhovskyi.zero.transactions

fun interface OnShowBreakdownHandler {

    fun onShowBreakdown()

    object Noop : OnShowBreakdownHandler {
        override fun onShowBreakdown() = Unit
    }
}
