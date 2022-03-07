package com.hluhovskyi.zero.common

data class Account(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
)