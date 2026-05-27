package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate

/**
 * Source of live exchange rates. A single [latest] call returns the whole table relative to one
 * base currency; callers derive any pair locally via cross-rates. Returns `null` when rates are
 * unavailable (network failure, server error), letting callers fall back to their own source.
 */
interface ExchangeRateService {

    suspend fun latest(): ExchangeRateSnapshot?

    object Noop : ExchangeRateService {
        override suspend fun latest(): ExchangeRateSnapshot? = null
    }
}

/** Exchange rates of [base] → each target currency, as returned by a single provider call. */
data class ExchangeRateSnapshot(
    val base: Id.Known,
    val rates: Map<Id.Known, Rate>,
)
