package com.hluhovskyi.zero.analytics

fun interface OnCashFlowTrendsSelectedHandler {
    fun onCashFlowTrendsSelected()

    companion object {
        val Noop = OnCashFlowTrendsSelectedHandler { }
    }
}
