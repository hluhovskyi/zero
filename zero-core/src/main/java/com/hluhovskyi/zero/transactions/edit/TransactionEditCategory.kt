package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id

data class TransactionEditCategory(
    val id: Id.Known,
    val name: String,
)