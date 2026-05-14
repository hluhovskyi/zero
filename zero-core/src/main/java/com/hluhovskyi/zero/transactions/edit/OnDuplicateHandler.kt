package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id

fun interface OnDuplicateHandler {
    fun onDuplicate(transactionId: Id.Known)

    object Noop : OnDuplicateHandler {
        override fun onDuplicate(transactionId: Id.Known) = Unit
    }
}
