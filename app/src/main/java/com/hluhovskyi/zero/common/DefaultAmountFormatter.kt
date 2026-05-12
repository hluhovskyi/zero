package com.hluhovskyi.zero.common

import java.text.DecimalFormat

internal class DefaultAmountFormatter(
    private val pattern: String = "#,##0.00",
) : AmountFormatter {

    override fun format(amount: Amount, currencySymbol: String): String {
        val isNegative = amount.value.signum() < 0
        val formatted = DecimalFormat(pattern).format(amount.value.abs())
        return if (isNegative) "-$currencySymbol$formatted" else "$currencySymbol$formatted"
    }
}
