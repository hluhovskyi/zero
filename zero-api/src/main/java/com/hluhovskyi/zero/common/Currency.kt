package com.hluhovskyi.zero.common

/** Currency definition with display [symbol] (e.g. "$", "EUR"). */
data class Currency(
    override val id: Id.Known,
    val name: String,
    val symbol: String,
) : Identifiable
