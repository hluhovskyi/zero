package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StubCurrencyRepository : CurrencyRepository {

    override fun query(criteria: CurrencyRepository.Criteria): Flow<List<Currency>> =
        flowOf(
            listOf(
                Currency(
                    id = Id("UAH"),
                    name = "UAH",
                    symbol = '₴',
                ),
                Currency(
                    id = Id("USD"),
                    name = "USD",
                    symbol = '$',
                ),
                Currency(
                    id = Id("EUR"),
                    name = "EUR",
                    symbol = '€',
                ),
            )
        )
}