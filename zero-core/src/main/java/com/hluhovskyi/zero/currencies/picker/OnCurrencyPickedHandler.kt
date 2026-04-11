package com.hluhovskyi.zero.currencies.picker

import com.hluhovskyi.zero.common.Currency

fun interface OnCurrencyPickedHandler {

    fun onPicked(currency: Currency)

    object Noop : OnCurrencyPickedHandler {
        override fun onPicked(currency: Currency) = Unit
    }
}
