package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface CurrencyRepository {

    fun query(criteria: Criteria): Flow<List<Currency>>

    sealed interface Criteria {

        class All : Criteria
    }

    object Noop : CurrencyRepository {
        override fun query(criteria: Criteria): Flow<List<Currency>> = flowOf(emptyList())
    }
}