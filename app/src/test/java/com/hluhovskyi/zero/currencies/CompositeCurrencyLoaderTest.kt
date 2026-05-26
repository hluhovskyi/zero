package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeCurrencyLoaderTest {

    private val usd = Id("USD")
    private val eur = Id("EUR")
    private val btc = Id("BTC")

    private class FakeLoader(
        private val rates: Map<Id.Known, Rate>,
        private val available: Set<Id.Known> = emptySet(),
    ) : CurrencyLoader {
        var ratesCalls = 0
        override suspend fun availableCurrencies() = available
        override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
            ratesCalls++
            return rates
        }
    }

    private class FakeService(private val rates: Map<Id.Known, Rate>) : ExchangeRateService {
        var calls = 0
        override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> {
            calls++
            return rates
        }
    }

    @Test
    fun `live rates override bundled and bundled-only keys survive`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))
        val service = FakeService(mapOf(eur to Rate(0.86)))
        val loader = CompositeCurrencyLoader(delegate, service)

        val rates = loader.ratesFor(usd)

        assertEquals(Rate(0.86).value, rates.getValue(eur).value) // live wins
        assertEquals(Rate(0.00002).value, rates.getValue(btc).value) // bundled-only survives
    }

    @Test
    fun `empty live map falls back to bundled only`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80), btc to Rate(0.00002)))
        val loader = CompositeCurrencyLoader(delegate, FakeService(emptyMap()))

        val rates = loader.ratesFor(usd)

        assertEquals(mapOf(eur to Rate(0.80).value, btc to Rate(0.00002).value), rates.mapValues { it.value.value })
    }

    @Test
    fun `second call for same base is served from cache`() = runTest {
        val delegate = FakeLoader(mapOf(eur to Rate(0.80)))
        val service = FakeService(mapOf(eur to Rate(0.86)))
        val loader = CompositeCurrencyLoader(delegate, service)

        loader.ratesFor(usd)
        loader.ratesFor(usd)

        assertEquals(1, delegate.ratesCalls)
        assertEquals(1, service.calls)
    }

    @Test
    fun `availableCurrencies delegates`() = runTest {
        val delegate = FakeLoader(rates = emptyMap(), available = setOf(usd, eur))
        val loader = CompositeCurrencyLoader(delegate, FakeService(emptyMap()))

        assertEquals(setOf(usd, eur), loader.availableCurrencies())
    }
}
