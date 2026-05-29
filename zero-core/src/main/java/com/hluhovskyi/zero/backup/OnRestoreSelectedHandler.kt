package com.hluhovskyi.zero.backup

fun interface OnRestoreSelectedHandler {

    fun onSelected()

    object Noop : OnRestoreSelectedHandler {
        override fun onSelected() = Unit
    }
}
