package com.hluhovskyi.zero.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

internal class DefaultAmountFormatter(
    private val pattern: String = "#,##0.00",
) : AmountFormatter {

    override fun format(amount: Amount, currencySymbol: String, style: AmountFormatter.Style): String {
        val isNegative = amount.value.signum() < 0
        val magnitude = amount.value.abs()
        val formatted = when (style) {
            AmountFormatter.Style.Full -> DecimalFormat(pattern).format(magnitude)
            AmountFormatter.Style.Short -> formatShort(magnitude)
        }
        return if (isNegative) "-$currencySymbol$formatted" else "$currencySymbol$formatted"
    }

    private fun formatShort(magnitude: BigDecimal): String = when {
        magnitude < THOUSAND -> DecimalFormat("#,##0.##").format(magnitude)
        magnitude < MILLION -> compact(magnitude, THOUSAND, "K")
        magnitude < BILLION -> compact(magnitude, MILLION, "M")
        else -> compact(magnitude, BILLION, "B")
    }

    private fun compact(magnitude: BigDecimal, unit: BigDecimal, suffix: String): String {
        val scaled = magnitude.divide(unit, 1, RoundingMode.HALF_UP)
        return DecimalFormat("0.#").format(scaled) + suffix
    }

    private companion object {
        val THOUSAND = BigDecimal(1_000)
        val MILLION = BigDecimal(1_000_000)
        val BILLION = BigDecimal(1_000_000_000)
    }
}
