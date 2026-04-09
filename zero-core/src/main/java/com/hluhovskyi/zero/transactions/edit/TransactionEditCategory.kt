package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image

data class TransactionEditCategory(
    override val id: Id.Known,
    val name: String,
    val colorScheme: ColorScheme,
    val icon: Image,
) : Identifiable
