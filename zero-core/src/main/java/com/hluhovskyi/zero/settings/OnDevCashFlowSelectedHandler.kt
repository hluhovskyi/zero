package com.hluhovskyi.zero.settings

fun interface OnDevCashFlowSelectedHandler {

    fun onSelected()

    object Noop : OnDevCashFlowSelectedHandler {
        override fun onSelected() = Unit
    }
}
