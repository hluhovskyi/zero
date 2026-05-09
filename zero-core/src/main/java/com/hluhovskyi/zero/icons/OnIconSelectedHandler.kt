package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorScheme

fun interface OnIconSelectedHandler {

    fun onIconSelected(icon: Icon, colorScheme: ColorScheme?)

    object Noop : OnIconSelectedHandler {
        override fun onIconSelected(icon: Icon, colorScheme: ColorScheme?) = Unit
    }
}
