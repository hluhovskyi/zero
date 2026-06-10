package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate

/** Domain filter for a transaction query: date range + category/account scoping, a null dimension is "any". */
data class TransactionFilterCriteria(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val categoryIds: Set<Id.Known>? = null,
    val accountIds: Set<Id.Known>? = null,
)
