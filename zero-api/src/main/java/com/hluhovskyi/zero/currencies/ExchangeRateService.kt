package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate

/**
 * Source of live exchange rates for a base currency, keyed by target currency.
 * Returns an empty map when rates are unavailable (network failure, unsupported base);
 * callers treat empty as "no live rates" and fall back to their own source.
 */
interface ExchangeRateService {

    suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate>

    object Noop : ExchangeRateService {
        override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> = emptyMap()
    }
}
