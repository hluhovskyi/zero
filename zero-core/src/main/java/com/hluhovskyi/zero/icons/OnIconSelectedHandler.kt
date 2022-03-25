package com.hluhovskyi.zero.icons

fun interface OnIconSelectedHandler {

    fun onIconSelected(icon: Icon)

    object Noop : OnIconSelectedHandler {
        override fun onIconSelected(icon: Icon) = Unit
    }
}