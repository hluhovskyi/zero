package com.hluhovskyi.zero.common

interface AmountFormatter {

    fun format(amount: Amount, currencySymbol: String = ""): String
}