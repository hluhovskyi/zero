package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

sealed interface TransactionFilter {
    data object All : TransactionFilter
    data class ForCategory(val categoryId: Id.Known) : TransactionFilter
    data class ForAccount(val accountId: Id.Known) : TransactionFilter
}
