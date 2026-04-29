package com.hluhovskyi.zero.common

import java.math.BigDecimal

/**
 * Money value wrapping [BigDecimal]. Supports arithmetic and currency conversion via [withRate].
 *
 * - `Amount(null)` returns [zero], not a crash — safe for `toBigDecimalOrNull()` results.
 * - `Amount.zero()` is a shared singleton.
 */
interface Amount {

    val value: BigDecimal

    fun withRate(rate: Rate): Amount

    operator fun plus(amount: Amount): Amount

    operator fun minus(amount: Amount): Amount

    operator fun div(amount: Amount): Double

    operator fun compareTo(value: Amount): Int

    operator fun compareTo(value: Long): Int

    companion object {

        private val ZERO = ValueAmount(BigDecimal.ZERO)

        fun zero(): Amount = ZERO

        operator fun invoke(value: Double): Amount = ValueAmount(BigDecimal.valueOf(value))

        operator fun invoke(value: Long): Amount = ValueAmount(BigDecimal.valueOf(value))

        operator fun invoke(value: BigDecimal?): Amount = value?.let(::ValueAmount) ?: zero()
    }
}

private class ValueAmount(override val value: BigDecimal) : Amount {

    override fun withRate(rate: Rate): Amount = ValueAmount(value.multiply(rate.value))

    override fun plus(amount: Amount): Amount = ValueAmount(value + amount.value)

    override fun minus(amount: Amount): Amount = ValueAmount(value - amount.value)

    override fun div(amount: Amount): Double = value.toDouble() / amount.value.toDouble()

    override fun compareTo(value: Amount): Int = this.value.compareTo(value.value)

    override fun compareTo(value: Long): Int = this.value.toLong().compareTo(value)
}
