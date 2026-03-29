package com.hluhovskyi.zero.currencies

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class InUseCurrencyRepository : CurrencyRepository {

    override fun <T> query(criteria: CurrencyRepository.Criteria<T>): Flow<T> {
        if (criteria !is CurrencyRepository.Criteria.InUse) {
            return emptyFlow()
        }

        return emptyFlow()
    }
}