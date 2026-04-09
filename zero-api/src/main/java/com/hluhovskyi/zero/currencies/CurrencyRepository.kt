package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CurrencyRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    fun interface Transformer {
        fun transform(repository: CurrencyRepository): CurrencyRepository
    }

    sealed interface Criteria<T> {

        class All : Criteria<List<Currency>>

        class InUse : Criteria<List<Currency>>

        data class ById(val id: Id.Known) : Criteria<Currency>
    }

    object Noop : CurrencyRepository {
        override fun <T> query(criteria: Criteria<T>): Flow<T> = emptyFlow()
    }
}
