package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id

data class TransactionEditAccount(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
)