package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Color
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

data class TransactionEditCategory(
    val id: Id.Known,
    val name: String,
    val color: Color,
    val icon: Image
)