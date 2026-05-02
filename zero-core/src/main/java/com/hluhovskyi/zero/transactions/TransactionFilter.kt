package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id

sealed interface TransactionFilter {
    object All : TransactionFilter
    data class ForCategory(val categoryId: Id.Known) : TransactionFilter
}
