package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.toBigDecimalOrZero
import java.math.BigDecimal
import java.math.RoundingMode

/** Decimal places kept for stored money amounts and the conversion rate. */
internal const val TRANSACTION_MONEY_SCALE = 2
internal const val TRANSACTION_RATE_SCALE = 6

/** Destination amount = `from × rate`, money-scaled to a clean string. */
internal fun receivedAmount(from: String, rate: String): String = (from.toBigDecimalOrZero() * rate.toBigDecimalOrZero())
    .setScale(TRANSACTION_MONEY_SCALE, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

/** rate = `to ÷ from` (rate-scaled). Null when `from` is 0/blank, so the caller keeps the old rate. */
internal fun rateFromAmounts(from: String, to: String): String? {
    val source = from.toBigDecimalOrNull() ?: return null
    if (source.signum() == 0) return null
    return to.toBigDecimalOrZero()
        .divide(source, TRANSACTION_RATE_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/** A derived rate, money-scaled to a clean string. */
internal fun BigDecimal.toRateString(): String = setScale(TRANSACTION_RATE_SCALE, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()
