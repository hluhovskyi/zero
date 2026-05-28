package com.hluhovskyi.zero.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency exchange rate multiplier. Used with [Amount.withRate] for conversion display.
 *
 * - [Same] represents no conversion (rate = 1.0). Used when transaction currency matches account currency.
 * - `Rate(null)` returns [Same].
 */
interface Rate {

    val value: BigDecimal

    object Same : Rate {
        override val value: BigDecimal = BigDecimal.valueOf(1)
    }

    companion object {

        operator fun invoke(value: Double): Rate = ValueRate(BigDecimal.valueOf(value))

        operator fun invoke(value: BigDecimal?): Rate = value?.let(::ValueRate) ?: Same
    }
}

private class ValueRate(override val value: BigDecimal) : Rate

private const val RATE_DIVISION_SCALE = 10

/** Divides one rate by another (`a / b`) with a fixed scale and HALF_UP rounding — used for cross-rates. */
operator fun Rate.div(divisor: Rate): Rate = Rate(value.divide(divisor.value, RATE_DIVISION_SCALE, RoundingMode.HALF_UP))
