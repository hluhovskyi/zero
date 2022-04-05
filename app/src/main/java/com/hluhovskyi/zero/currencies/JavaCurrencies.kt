package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import java.util.Locale
import java.util.Currency as JavaCurrency

fun JavaCurrency.toCurrency(locale: Locale): Currency =
    Currency(
        id = Id(currencyCode),
        name = getDisplayName(locale),
        symbol = getSymbol(locale)
    )