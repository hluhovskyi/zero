package com.hluhovskyi.zero.accounts.detail

fun interface OnAccountDetailCreateTransactionHandler {
    fun onCreate()

    object Noop : OnAccountDetailCreateTransactionHandler {
        override fun onCreate() = Unit
    }
}
