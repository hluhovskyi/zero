package com.hluhovskyi.zero.accounts.edit

fun interface OnCloseHandler {

    fun onClose()

    object Noop : OnCloseHandler {
        override fun onClose() = Unit
    }
}
