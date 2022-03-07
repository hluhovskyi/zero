package com.hluhovskyi.zero.common

import java.math.BigDecimal

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