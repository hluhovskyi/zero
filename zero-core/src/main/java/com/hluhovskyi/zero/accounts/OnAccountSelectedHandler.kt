package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id

fun interface OnAccountSelectedHandler {

    fun onSelected(accountId: Id.Known)

    object Noop : OnAccountSelectedHandler {
        override fun onSelected(accountId: Id.Known) = Unit
    }
}
