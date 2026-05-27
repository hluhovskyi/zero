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

    private class FakeLoader(
        private val rates: Map<Id.Known, Rate>,
        private val available: Set<Id.Known> = emptySet(),
    ) : CurrencyLoader {
        override suspend fun availableCurrencies() = available
        override suspend fun ratesFor(currencyId: Id.Known) = rates
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

    private fun divide(numerator: Double, denominator: Double): BigDecimal =
        BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 10, RoundingMode.HALF_UP)

    private fun loader(
        bundled: FakeLoader,
        service: FakeService,
        clock: FakeClock,
        store: RateSnapshotStore = RateSnapshotStore(FakeConfigurationRepository()),
    ) = CompositeCurrencyLoader(bundled, service, store, clock)

    @Test
    fun `live cross-rates override bundled while bundled-only keys survive`() = runTest {
        val bundled = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))
        val service = FakeService(eurSnapshot(usd to 1.16, gbp to 0.86))

        val rates = loader(bundled, service, FakeClock(LocalDate(2026, 5, 26))).ratesFor(usd)

        assertEquals(divide(1.0, 1.16), rates.getValue(eur).value) // USD->EUR, live wins over bundled 0.80
        assertEquals(divide(0.86, 1.16), rates.getValue(gbp).value) // USD->GBP cross-rate
        assertEquals(Rate.Same.value, rates.getValue(usd).value) // USD->USD
        assertEquals(Rate(0.00002).value, rates.getValue(btc).value) // bundled-only survives
    }

    @Test
    fun `null snapshot falls back to bundled only`() = runTest {
        val bundled = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))

        val rates = loader(bundled, FakeService(null), FakeClock(LocalDate(2026, 5, 26))).ratesFor(usd)

        assertEquals(mapOf(eur to Rate(0.80).value, btc to Rate(0.00002).value), rates.mapValues { it.value.value })
    }

    @Test
    fun `snapshot is fetched at most once per day across currencies`() = runTest {
        val service = FakeService(eurSnapshot(usd to 1.16, gbp to 0.86))
        val loader = loader(FakeLoader(emptyMap()), service, FakeClock(LocalDate(2026, 5, 26)))

        loader.ratesFor(usd)
        loader.ratesFor(usd)
        loader.ratesFor(gbp)

        assertEquals(1, service.calls)
    }

    @Test
    fun `a new day triggers a refetch and fresh cross-rates`() = runTest {
        val service = FakeService(eurSnapshot(usd to 1.16))
        val clock = FakeClock(LocalDate(2026, 5, 26))
        val loader = loader(FakeLoader(emptyMap()), service, clock)

        val day1 = loader.ratesFor(usd).getValue(eur).value
        service.snapshot = eurSnapshot(usd to 2.0)
        clock.date = LocalDate(2026, 5, 27)
        val day2 = loader.ratesFor(usd).getValue(eur).value

        assertEquals(2, service.calls)
        assertEquals(divide(1.0, 1.16), day1)
        assertEquals(divide(1.0, 2.0), day2)
    }

    @Test
    fun `today's persisted snapshot is reused without fetching`() = runTest {
        val store = RateSnapshotStore(FakeConfigurationRepository())
        store.save(RateSnapshotStore.Stored(fetchedOn = LocalDate(2026, 5, 26), base = "EUR", rates = mapOf("USD" to 1.16)))
        val service = FakeService(eurSnapshot(usd to 9.99))

        val rates = loader(FakeLoader(emptyMap()), service, FakeClock(LocalDate(2026, 5, 26)), store).ratesFor(usd)

        assertEquals(0, service.calls)
        assertEquals(divide(1.0, 1.16), rates.getValue(eur).value)
    }

    @Test
    fun `stale persisted snapshot is used when today's fetch fails`() = runTest {
        val store = RateSnapshotStore(FakeConfigurationRepository())
        store.save(RateSnapshotStore.Stored(fetchedOn = LocalDate(2026, 5, 25), base = "EUR", rates = mapOf("USD" to 1.16)))

        val rates = loader(FakeLoader(emptyMap()), FakeService(null), FakeClock(LocalDate(2026, 5, 26)), store).ratesFor(usd)

        assertEquals(divide(1.0, 1.16), rates.getValue(eur).value)
    }

    @Test
    fun `availableCurrencies delegates`() = runTest {
        val bundled = FakeLoader(rates = emptyMap(), available = setOf(usd, eur))

        val available = loader(bundled, FakeService(null), FakeClock(LocalDate(2026, 5, 26))).availableCurrencies()

        assertEquals(setOf(usd, eur), available)
    }
}
