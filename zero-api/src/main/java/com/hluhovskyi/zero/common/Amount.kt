package com.hluhovskyi.zero.common

import java.math.BigDecimal

interface Amount {

    val value: BigDecimal

    operator fun plus(amount: Amount): Amount

    operator fun minus(amount: Amount): Amount

    companion object {

        private val ZERO = ValueAmount(BigDecimal.ZERO)

        fun zero(): Amount = ZERO

        operator fun invoke(value: Double): Amount = ValueAmount(BigDecimal.valueOf(value))

        operator fun invoke(value: Long): Amount = ValueAmount(BigDecimal.valueOf(value))

        operator fun invoke(value: BigDecimal?): Amount = value?.let(::ValueAmount) ?: zero()
    }
}

private class ValueAmount(override val value: BigDecimal) : Amount {

    override fun plus(amount: Amount): Amount = ValueAmount(value + amount.value)

    override fun minus(amount: Amount): Amount = ValueAmount(value - amount.value)
}
