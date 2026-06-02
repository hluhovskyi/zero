package com.hluhovskyi.zero.settings

fun interface OnBackupSelectedHandler {

    fun onSelected()

    object Noop : OnBackupSelectedHandler {
        override fun onSelected() = Unit
    }
}
