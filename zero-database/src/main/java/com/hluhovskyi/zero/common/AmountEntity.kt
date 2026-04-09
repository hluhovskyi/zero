package com.hluhovskyi.zero.common

import java.math.BigDecimal

internal data class AmountEntity(
    val value: BigDecimal,
) {

    companion object {

        private val EMPTY = AmountEntity(BigDecimal.ZERO)

        fun empty(): AmountEntity = EMPTY
    }
}
