package com.hluhovskyi.zero.transactions

import kotlinx.datetime.LocalDateTime

internal data class CategoryUsageStatistic(
    val categoryId: String,
    val transactionCount: Int,
    val lastUsedDateTime: LocalDateTime,
)
