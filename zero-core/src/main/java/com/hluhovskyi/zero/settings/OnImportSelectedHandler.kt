package com.hluhovskyi.zero.settings

fun interface OnImportSelectedHandler {

    fun onSelected()

    object Noop : OnImportSelectedHandler {
        override fun onSelected() = Unit
    }
}
