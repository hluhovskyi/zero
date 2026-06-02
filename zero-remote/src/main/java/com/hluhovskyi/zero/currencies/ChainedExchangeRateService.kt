package com.hluhovskyi.zero.currencies

/** Merges [tiers] (low → high priority) — higher overrides per currency. All tiers must emit the same base. */
internal class ChainedExchangeRateService(
    private val tiers: List<ExchangeRateService>,
) : ExchangeRateService {

    override suspend fun latest(): ExchangeRateSnapshot? {
        var merged: ExchangeRateSnapshot? = null
        for (tier in tiers) {
            val snapshot = tier.latest() ?: continue
            merged = merged?.let { ExchangeRateSnapshot(snapshot.base, it.rates + snapshot.rates) } ?: snapshot
        }
        return merged
    }
}
