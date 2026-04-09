package com.hluhovskyi.zero.accounts.edit

fun interface OnAccountSavedHandler {

    fun onSaved()

    object Noop : OnAccountSavedHandler {
        override fun onSaved() = Unit
    }
}
