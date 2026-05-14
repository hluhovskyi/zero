package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

fun interface OnDuplicateTransactionHandler {

    fun onDuplicate(transactionId: Id.Known)

    object Noop : OnDuplicateTransactionHandler {
        override fun onDuplicate(transactionId: Id.Known) = Unit
    }
}
