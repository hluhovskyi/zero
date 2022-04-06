package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable

data class TransactionEditAccount(
    override val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
) : Identifiable