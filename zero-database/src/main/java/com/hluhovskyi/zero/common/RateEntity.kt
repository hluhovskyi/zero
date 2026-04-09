package com.hluhovskyi.zero.common

import java.math.BigDecimal

internal data class RateEntity(
    val value: BigDecimal,
) {

    companion object {

        private val EMPTY = RateEntity(BigDecimal.ZERO)

        fun empty(): RateEntity = EMPTY
    }
}
