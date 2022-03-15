package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id

data class Account(
    val id: Id.Known,
    val name: String,
    val balance: Amount
)