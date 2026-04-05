package com.hluhovskyi.zero.transactions

import java.math.BigDecimal

internal data class CategoryAmountStatistic(
    val categoryId: String,
    val averageAmount: BigDecimal,
)
