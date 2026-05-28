package com.hluhovskyi.zero.currencies

/**
 * Layers multiple [ExchangeRateService] tiers into one snapshot. [tiers] are ordered **low → high
 * priority**: each reachable tier contributes its rates and higher-priority tiers override lower
 * ones per currency (so the authoritative source wins where it has a rate, broader sources fill the
 * rest). Unreachable tiers (`latest() == null`) are skipped; returns `null` only when every tier is
 * unreachable, letting the caller fall back to its bundled rates.
 *
 * All tiers must emit the same base currency (EUR); the merged snapshot keeps it.
 */
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
