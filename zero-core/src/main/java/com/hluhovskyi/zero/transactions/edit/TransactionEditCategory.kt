package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image

data class TransactionEditCategory(
    override val id: Id.Known,
    val name: String,
    val color: ColorValue,
    val icon: Image
) : Identifiable