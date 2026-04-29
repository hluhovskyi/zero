package com.hluhovskyi.zero.transactions

import java.math.BigDecimal

internal data class CategorySpendingRow(
    val categoryId: String,
    val currencyId: String,
    val totalAmount: BigDecimal,
    val transactionCount: Int,
)
