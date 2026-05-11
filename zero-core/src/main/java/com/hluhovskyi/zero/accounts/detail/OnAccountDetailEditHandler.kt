package com.hluhovskyi.zero.accounts.detail

fun interface OnAccountDetailEditHandler {

    fun onEdit()

    object Noop : OnAccountDetailEditHandler {
        override fun onEdit() = Unit
    }
}
