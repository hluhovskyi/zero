package com.hluhovskyi.zero.transactions.edit

fun interface OnTransactionSavedHandler {
    fun onSaved()

    object Noop : OnTransactionSavedHandler {
        override fun onSaved() = Unit
    }
}