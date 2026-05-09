package com.hluhovskyi.zero.colors

fun interface OnColorSelectedHandler {

    fun onColorSelected(color: Color, colorScheme: ColorScheme)

    object Noop : OnColorSelectedHandler {
        override fun onColorSelected(color: Color, colorScheme: ColorScheme) = Unit
    }
}
