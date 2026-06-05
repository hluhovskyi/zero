package com.hluhovskyi.zero.settings

fun interface OnDevChartsSelectedHandler {

    fun onSelected()

    object Noop : OnDevChartsSelectedHandler {
        override fun onSelected() = Unit
    }
}
