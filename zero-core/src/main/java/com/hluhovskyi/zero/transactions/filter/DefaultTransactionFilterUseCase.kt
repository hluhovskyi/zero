package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DefaultTransactionFilterUseCase : TransactionFilterUseCase {

    private val _activeFilter = MutableStateFlow(TransactionFilter())
    override val activeFilter: StateFlow<TransactionFilter> = _activeFilter

    override fun apply(filter: TransactionFilter) {
        _activeFilter.value = filter
    }
}
