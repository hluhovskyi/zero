package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Currency as JavaCurrency

internal class JavaCurrencyRepository(
    private val localeProvider: LocaleProvider,
    private val currencyTransformer: (Currency) -> Currency = { currency ->
        when (currency.id) {
            Id("UAH") -> currency.copy(symbol = "₴")
            else -> currency
        }
    },
    private val currencyLoader: CurrencyLoader,
) : CurrencyRepository {

    private val currencies = lazy {
        JavaCurrency.getAvailableCurrencies()
            .asSequence()
            .map { currency -> currency.toCurrency(localeProvider.locale()) }
            .map(currencyTransformer)
            .sortedBy { currency -> currency.name }
            .associateBy { it.id }
    }

    override fun <T> query(criteria: CurrencyRepository.Criteria<T>): Flow<T> =
        when (criteria) {
            is CurrencyRepository.Criteria.All -> flow {
                val availableCurrencies = currencyLoader.availableCurrencies()

                emit(currencies.value
                    .mapNotNull { (id, currency) -> currency.takeIf { id in availableCurrencies } }
                    .toList()
                )
            }.uncheckedCast()
            is CurrencyRepository.Criteria.ById -> flow { currencies.value[criteria.id]?.let { emit(it) } }.uncheckedCast()
        }
}