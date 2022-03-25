package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.LocaleProvider
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
    private val allowedCurrencies: Set<String> = setOf("UAH", "USD", "EUR")
) : CurrencyRepository {

    private val currencies = lazy {
        JavaCurrency.getAvailableCurrencies()
            .filter { currency -> currency.currencyCode in allowedCurrencies }
            .map { currency ->
                Currency(
                    id = Id(currency.currencyCode),
                    name = currency.getDisplayName(localeProvider.locale()),
                    symbol = currency.getSymbol(localeProvider.locale())
                )
            }
            .map(currencyTransformer)
            .sortedBy { currency -> currency.name }
    }

    override fun query(criteria: CurrencyRepository.Criteria): Flow<List<Currency>> =
        flow { emit(currencies.value) }
}