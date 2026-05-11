package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id

fun interface OnEditAccountHandler {

    fun onEdit(accountId: Id.Known)

    object Noop : OnEditAccountHandler {
        override fun onEdit(accountId: Id.Known) = Unit
    }
}
