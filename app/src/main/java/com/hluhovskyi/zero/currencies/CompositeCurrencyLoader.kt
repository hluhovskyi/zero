package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import java.util.concurrent.ConcurrentHashMap

/**
 * Merges live rates from [exchangeRateService] over a bundled [delegate] loader.
 * Live rates win on key overlap; keys the live source omits (e.g. crypto) keep their
 * bundled values. An empty live map (network failure / unsupported base) yields the
 * bundled rates unchanged. Merged results are cached per base for the session.
 */
internal class CompositeCurrencyLoader(
    private val delegate: CurrencyLoader,
    private val exchangeRateService: ExchangeRateService,
) : CurrencyLoader {

    private val mergedRates = ConcurrentHashMap<Id.Known, Map<Id.Known, Rate>>()

    override suspend fun availableCurrencies(): Set<Id.Known> = delegate.availableCurrencies()

    override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
        mergedRates[currencyId]?.let { return it }

        val bundled = delegate.ratesFor(currencyId)
        val live = exchangeRateService.ratesFor(currencyId)
        val merged = if (live.isEmpty()) bundled else bundled + live
        return merged.also { mergedRates[currencyId] = it }
    }
}
