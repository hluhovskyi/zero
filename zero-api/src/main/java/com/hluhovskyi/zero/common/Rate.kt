package com.hluhovskyi.zero.common

import java.math.BigDecimal

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
