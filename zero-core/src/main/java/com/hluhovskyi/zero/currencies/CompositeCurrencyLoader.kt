package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.div
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * [CurrencyLoader] that serves the [bundled] offline snapshot with live [exchangeRateService] rates
 * layered on top.
 *
 * Both the bundle and the live response are EUR-based, so their rate maps merge directly: live fiat
 * overrides the bundled baseline, while currencies the live source omits (BTC/ETH) keep their
 * bundled values. The live snapshot is fetched at most once per calendar day (per [clock]) and
 * persisted via [store]; offline sessions reuse the last persisted snapshot, then just the bundle.
 * Any pair is derived from the merged EUR table by cross-rate (`from→to = table[to] / table[from]`).
 */
internal class CompositeCurrencyLoader(
    private val bundled: BundledExchangeRatesSource,
    private val exchangeRateService: ExchangeRateService,
    private val store: RateSnapshotStore,
    private val clock: ZonedClock,
) : CurrencyLoader {

    private val crossRateCache = ConcurrentHashMap<Id.Known, Map<Id.Known, Rate>>()
    private val refreshMutex = Mutex()

    @Volatile
    private var merged: ExchangeRateSnapshot? = null

    @Volatile
    private var refreshedDay: LocalDate? = null

    /** Stable and network-free: the offline bundle defines which currencies are supported. */
    override suspend fun availableCurrencies(): Set<Id.Known> {
        val snapshot = bundled.load()
        return buildSet {
            add(snapshot.base)
            addAll(snapshot.rates.keys)
        }
    }

    override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
        val snapshot = mergedSnapshot()
        crossRateCache[currencyId]?.let { return it }
        return crossRates(snapshot, currencyId).also { crossRateCache[currencyId] = it }
    }

    /** The bundle merged with the live snapshot, refreshed at most once per calendar day. */
    private suspend fun mergedSnapshot(): ExchangeRateSnapshot {
        val today = clock.localDateTime().date
        merged?.let { if (refreshedDay == today) return it }

        return refreshMutex.withLock {
            merged?.let { if (refreshedDay == today) return@withLock it }
            crossRateCache.clear()

            val bundledSnapshot = bundled.load()
            val live = liveRates(today)
            ExchangeRateSnapshot(
                base = bundledSnapshot.base,
                rates = if (live.isEmpty()) bundledSnapshot.rates else bundledSnapshot.rates + live,
            )
                .also { merged = it }
                .also { refreshedDay = today }
        }
    }

    /** EUR-based live rates for [today] — fetched once, persisted, with a stale-then-empty fallback. */
    private suspend fun liveRates(today: LocalDate): Map<Id.Known, Rate> {
        val stored = store.load()
        if (stored != null && stored.fetchedOn == today) {
            return stored.toRates()
        }

        val fetched = exchangeRateService.latest()
        if (fetched != null) {
            store.save(fetched.toStored(today))
            return fetched.rates
        }

        return stored?.toRates().orEmpty()
    }

    /**
     * Derives the rate table for base [from] from the EUR-based [snapshot] using cross-rates:
     * `from→target = (EUR→target) / (EUR→from)`, with `from→[snapshot.base]` and `from→from = 1`
     * filled in. Returns an empty map when [from] is absent from the table.
     */
    private fun crossRates(snapshot: ExchangeRateSnapshot, from: Id.Known): Map<Id.Known, Rate> {
        // base → target is already the table; no division needed (and avoids scaling by 1).
        if (from == snapshot.base) {
            return HashMap<Id.Known, Rate>(snapshot.rates).apply { put(from, Rate.Same) }
        }

        val fromRate = snapshot.rates[from] ?: return emptyMap()
        val out = HashMap<Id.Known, Rate>(snapshot.rates.size + 2)
        out[snapshot.base] = Rate.Same / fromRate
        for ((target, rate) in snapshot.rates) {
            out[target] = rate / fromRate
        }
        out[from] = Rate.Same
        return out
    }
}

private fun RateSnapshotStore.Stored.toRates(): Map<Id.Known, Rate> = rates.entries.associate { (code, rate) -> Id(code) to Rate(rate) }

private fun ExchangeRateSnapshot.toStored(day: LocalDate): RateSnapshotStore.Stored = RateSnapshotStore.Stored(
    fetchedOn = day,
    base = base.value,
    rates = rates.entries.associate { (id, rate) -> id.value to rate.value.toDouble() },
)
