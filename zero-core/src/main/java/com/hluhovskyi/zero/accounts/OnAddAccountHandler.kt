package com.hluhovskyi.zero.accounts

fun interface OnAddAccountHandler {

    fun onAddAccount()

    object Noop : OnAddAccountHandler {
        override fun onAddAccount() = Unit
    }
}
