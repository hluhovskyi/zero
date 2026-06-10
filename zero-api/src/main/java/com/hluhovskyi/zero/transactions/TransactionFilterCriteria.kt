package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate

/**
 * Domain filter for a spending query: an optional date range plus optional category/account scoping
 * (a null dimension is "not filtered"). Lets use cases describe what to aggregate without depending
 * on the data layer — the repository implementation maps it to a [TransactionRepository.Criteria].
 */
data class TransactionFilterCriteria(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val categoryIds: Set<Id.Known>? = null,
    val accountIds: Set<Id.Known>? = null,
)
