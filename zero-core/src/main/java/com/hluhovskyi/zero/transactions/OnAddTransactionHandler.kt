package com.hluhovskyi.zero.transactions

fun interface OnAddTransactionHandler {

    fun onAddTransaction()

    object Noop : OnAddTransactionHandler {
        override fun onAddTransaction() = Unit
    }
}
