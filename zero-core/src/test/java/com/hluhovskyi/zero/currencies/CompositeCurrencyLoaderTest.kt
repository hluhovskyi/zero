package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CompositeCurrencyLoaderTest {

    private val eur = Id("EUR")
    private val usd = Id("USD")
    private val gbp = Id("GBP")
    private val btc = Id("BTC")

    private class FakeBundled(private val snapshot: ExchangeRateSnapshot) : BundledExchangeRatesSource {
        override suspend fun load(): ExchangeRateSnapshot = snapshot
    }

    private class FakeService(var snapshot: ExchangeRateSnapshot?) : ExchangeRateService {
        var calls = 0
        override suspend fun latest(): ExchangeRateSnapshot? {
            calls++
            return snapshot
        }
    }

    private class FakeClock(var date: LocalDate) : ZonedClock {
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC)
        override fun timeZone(): TimeZone = TimeZone.UTC
    }

    private fun eurSnapshot(vararg rates: Pair<Id.Known, Double>) = ExchangeRateSnapshot(
        base = eur,
        rates = rates.associate { (id, value) -> id to Rate(value) },
    )

    private fun divide(numerator: Double, denominator: Double): BigDecimal = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 10, RoundingMode.HALF_UP)

    private fun loader(
        bundled: ExchangeRateSnapshot,
        service: FakeService = FakeService(null),
        clock: FakeClock = FakeClock(LocalDate(2026, 5, 27)),
        store: RateSnapshotStore = ConfigurationRateSnapshotStore(FakeConfigurationRepository()),
    ) = CompositeCurrencyLoader(FakeBundled(bundled), service, store, clock)

    @Test
    fun `availableCurrencies is the bundle's currencies plus the base`() = runTest {
        val available = loader(eurSnapshot(usd to 1.16, btc to 0.00002)).availableCurrencies()

        assertEquals(setOf(eur, usd, btc), available)
    }

    @Test
    fun `cross-rates are derived from the bundle when offline`() = runTest {
        val rates = loader(eurSnapshot(usd to 1.16, btc to 0.00002)).ratesFor(usd)

        assertEquals(divide(1.0, 1.16), rates.getValue(eur).value) // USD->EUR
        assertEquals(divide(0.00002, 1.16), rates.getValue(btc).value) // USD->BTC, bundled-only
        assertEquals(Rate.Same.value, rates.getValue(usd).value) // USD->USD
    }

    @Test
    fun `live rates override the bundled fiat while crypto survives`() = runTest {
        val bundled = eurSnapshot(usd to 1.00, btc to 0.00002) // stale fiat + crypto
        val service = FakeService(eurSnapshot(usd to 1.16)) // fresh fiat only

        val rates = loader(bundled, service).ratesFor(eur)

        assertEquals(Rate(1.16).value, rates.getValue(usd).value) // live won over stale 1.00
        assertEquals(Rate(0.00002).value, rates.getValue(btc).value) // bundled crypto survives
    }

    @Test
    fun `snapshot is fetched at most once per day across currencies`() = runTest {
        val service = FakeService(eurSnapshot(usd to 1.16, gbp to 0.86))
        val loader = loader(eurSnapshot(usd to 1.16, gbp to 0.86), service)

        loader.ratesFor(usd)
        loader.ratesFor(usd)
        loader.ratesFor(gbp)

        assertEquals(1, service.calls)
    }

    @Test
    fun `a new day triggers a refetch with fresh rates`() = runTest {
        val service = FakeService(eurSnapshot(usd to 1.16))
        val clock = FakeClock(LocalDate(2026, 5, 27))
        val loader = loader(eurSnapshot(usd to 1.16), service, clock)

        val day1 = loader.ratesFor(eur).getValue(usd).value
        service.snapshot = eurSnapshot(usd to 2.0)
        clock.date = LocalDate(2026, 5, 28)
        val day2 = loader.ratesFor(eur).getValue(usd).value

        assertEquals(2, service.calls)
        assertEquals(Rate(1.16).value, day1)
        assertEquals(Rate(2.0).value, day2)
    }

    @Test
    fun `today's persisted snapshot is reused without fetching`() = runTest {
        val store = ConfigurationRateSnapshotStore(FakeConfigurationRepository())
        store.save(RateSnapshotStore.Stored(fetchedOn = LocalDate(2026, 5, 27), base = "EUR", rates = mapOf("USD" to 1.16)))
        val service = FakeService(eurSnapshot(usd to 9.99))

        val rates = loader(eurSnapshot(usd to 1.00), service, store = store).ratesFor(eur)

        assertEquals(0, service.calls)
        assertEquals(Rate(1.16).value, rates.getValue(usd).value) // persisted live, not the 9.99 fetch
    }

    @Test
    fun `stale persisted snapshot is used when today's fetch fails`() = runTest {
        val store = ConfigurationRateSnapshotStore(FakeConfigurationRepository())
        store.save(RateSnapshotStore.Stored(fetchedOn = LocalDate(2026, 5, 26), base = "EUR", rates = mapOf("USD" to 1.16)))

        val rates = loader(eurSnapshot(usd to 1.00), FakeService(null), store = store).ratesFor(eur)

        assertEquals(Rate(1.16).value, rates.getValue(usd).value) // stale live still overrides bundled 1.00
    }
}
