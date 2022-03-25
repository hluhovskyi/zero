package com.hluhovskyi.zero.common

data class Currency(
    override val id: Id.Known,
    val name: String,
    val symbol: String
) : Identifiable
