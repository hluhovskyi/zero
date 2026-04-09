package com.hluhovskyi.zero.colors

fun interface OnColorSelectedHandler {

    fun onColorSelected(color: Color)

    object Noop : OnColorSelectedHandler {
        override fun onColorSelected(color: Color) = Unit
    }
}
