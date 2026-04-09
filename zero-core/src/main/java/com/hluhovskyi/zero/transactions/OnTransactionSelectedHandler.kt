package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

fun interface OnTransactionSelectedHandler {

    fun onSelected(transactionId: Id.Known)

    object Noop : OnTransactionSelectedHandler {
        override fun onSelected(transactionId: Id.Known) = Unit
    }
}
