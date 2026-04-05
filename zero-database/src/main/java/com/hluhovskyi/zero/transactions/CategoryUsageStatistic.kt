package com.hluhovskyi.zero.transactions

import java.time.LocalDateTime

internal data class CategoryUsageStatistic(
    val categoryId: String,
    val transactionCount: Int,
    val lastUsedDateTime: LocalDateTime,
)
