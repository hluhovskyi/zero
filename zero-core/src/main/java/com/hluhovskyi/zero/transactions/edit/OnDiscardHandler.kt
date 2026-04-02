package com.hluhovskyi.zero.transactions.edit

fun interface OnDiscardHandler {
    fun onDiscard()

    object Noop : OnDiscardHandler {
        override fun onDiscard() = Unit
    }
}
