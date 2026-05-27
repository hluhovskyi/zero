package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
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

    private fun crossRates(snapshot: ExchangeRateSnapshot, from: Id.Known): Map<Id.Known, Rate> {
        val fromRate = when (from) {
            snapshot.base -> BigDecimal.ONE
            else -> snapshot.rates[from]?.value ?: return emptyMap()
        }

        val out = HashMap<Id.Known, Rate>(snapshot.rates.size + 2)
        out[snapshot.base] = Rate(BigDecimal.ONE.divide(fromRate, RATE_SCALE, RoundingMode.HALF_UP))
        for ((target, rate) in snapshot.rates) {
            out[target] = Rate(rate.value.divide(fromRate, RATE_SCALE, RoundingMode.HALF_UP))
        }
        out[from] = Rate.Same
        return out
    }

    companion object {
        private const val RATE_SCALE = 10
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
