package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlinx.coroutines.flow.StateFlow

interface TransactionFilterUseCase {
    val activeFilter: StateFlow<TransactionFilter>
    fun apply(filter: TransactionFilter)
}
