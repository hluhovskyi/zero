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
 * Serves currency rates by layering a live [exchangeRateService] snapshot over the bundled
 * [delegate] loader.
 *
 * The live snapshot is fetched **at most once per calendar day** (per [clock]) and persisted via
 * [store], so restarts within a day reuse it and offline sessions fall back to the last persisted
 * snapshot. From that single base-relative table any pair is derived locally by cross-rate
 * (`from→to = table[to] / table[from]`). Bundled rates are merged underneath, so currencies the
 * live source omits (e.g. crypto like BTC) keep their bundled values; live rates win on overlap.
 */
internal class CompositeCurrencyLoader(
    private val delegate: CurrencyLoader,
    private val exchangeRateService: ExchangeRateService,
    private val store: RateSnapshotStore,
    private val clock: ZonedClock,
) : CurrencyLoader {

    private val crossRateCache = ConcurrentHashMap<Id.Known, Map<Id.Known, Rate>>()
    private val refreshMutex = Mutex()

    @Volatile
    private var snapshot: ExchangeRateSnapshot? = null

    @Volatile
    private var refreshedDay: LocalDate? = null

    override suspend fun availableCurrencies(): Set<Id.Known> = delegate.availableCurrencies()

    override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
        // Refresh first: a day rollover clears crossRateCache, so it never serves stale cross-rates.
        val snapshot = currentSnapshot()
        crossRateCache[currencyId]?.let { return it }

        val live = snapshot?.let { crossRates(it, currencyId) }.orEmpty()
        val bundled = delegate.ratesFor(currencyId)
        val merged = if (live.isEmpty()) bundled else bundled + live
        return merged.also { crossRateCache[currencyId] = it }
    }

    /** Today's snapshot, refreshing (network + persist) at most once per calendar day. */
    private suspend fun currentSnapshot(): ExchangeRateSnapshot? {
        val today = clock.localDateTime().date
        if (refreshedDay == today) {
            return snapshot
        }

        return refreshMutex.withLock {
            if (refreshedDay == today) {
                return@withLock snapshot
            }
            crossRateCache.clear()
            resolveForDay(today)
                .also { snapshot = it }
                .also { refreshedDay = today }
        }
    }

    private suspend fun resolveForDay(today: LocalDate): ExchangeRateSnapshot? {
        val stored = store.load()
        if (stored != null && stored.fetchedOn == today) {
            return stored.toSnapshot()
        }

        val fetched = exchangeRateService.latest()
        if (fetched != null) {
            store.save(fetched.toStored(today))
            return fetched
        }

        // Offline / server error: reuse the last persisted snapshot (stale) if any; the bundled
        // delegate is the final fallback when there is none.
        return stored?.toSnapshot()
    }

    /**
     * Derives the rate table for base [from] from the EUR-based [snapshot] using cross-rates:
     * `from→target = (EUR→target) / (EUR→from)`. A single daily fetch of the EUR table therefore
     * answers any pair, with `from→[snapshot.base]` and `from→from = 1` filled in explicitly.
     *
     * Returns an empty map when [from] is absent from the table — i.e. a currency the live source
     * does not cover (e.g. crypto like BTC) — so the caller falls back to bundled rates for it.
     */
    private fun crossRates(snapshot: ExchangeRateSnapshot, from: Id.Known): Map<Id.Known, Rate> {
        val fromRate = when (from) {
            snapshot.base -> Rate.Same
            else -> snapshot.rates[from] ?: return emptyMap()
        }

        val out = HashMap<Id.Known, Rate>(snapshot.rates.size + 2)
        out[snapshot.base] = Rate.Same / fromRate
        for ((target, rate) in snapshot.rates) {
            out[target] = rate / fromRate
        }
        out[from] = Rate.Same
        return out
    }
}

private fun RateSnapshotStore.Stored.toSnapshot(): ExchangeRateSnapshot = ExchangeRateSnapshot(
    base = Id(base),
    rates = rates.entries.associate { (code, rate) -> Id(code) to Rate(rate) },
)

private fun ExchangeRateSnapshot.toStored(day: LocalDate): RateSnapshotStore.Stored = RateSnapshotStore.Stored(
    fetchedOn = day,
    base = base.value,
    rates = rates.entries.associate { (id, rate) -> id.value to rate.value.toDouble() },
)
