package com.hluhovskyi.zero.common

import java.text.DecimalFormat

internal class DefaultAmountFormatter(
    private val pattern: String = "#.00",
) : AmountFormatter {

    override fun format(amount: Amount, currencySymbol: String): String = DecimalFormat(pattern).format(amount.value) + currencySymbol
}
