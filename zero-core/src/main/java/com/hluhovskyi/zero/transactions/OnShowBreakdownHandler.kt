package com.hluhovskyi.zero.transactions

fun interface OnShowBreakdownHandler {

    fun onShowBreakdown(filter: TransactionFilter)

    object Noop : OnShowBreakdownHandler {
        override fun onShowBreakdown(filter: TransactionFilter) = Unit
    }
}
