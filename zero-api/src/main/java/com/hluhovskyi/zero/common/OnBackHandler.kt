package com.hluhovskyi.zero.common

fun interface OnBackHandler {
    fun onBack()

    companion object {
        val Noop: OnBackHandler = OnBackHandler { }
    }
}
