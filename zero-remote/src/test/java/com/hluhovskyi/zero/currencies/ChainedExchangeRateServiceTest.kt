package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainedExchangeRateServiceTest {

    private val eur = Id("EUR")
    private val usd = Id("USD")
    private val btc = Id("BTC")

    private fun snapshot(vararg rates: Pair<Id.Known, Double>) = ExchangeRateSnapshot(eur, rates.associate { (id, value) -> id to Rate(value) })

    private fun service(snapshot: ExchangeRateSnapshot?) = object : ExchangeRateService {
        override suspend fun latest(): ExchangeRateSnapshot? = snapshot
    }

    @Test
    fun `higher-priority tier overrides lower per currency while lower fills the rest`() = runTest {
        val low = service(snapshot(usd to 1.00, btc to 0.00002)) // broad: stale fiat + crypto
        val high = service(snapshot(usd to 1.16)) // authoritative fiat

        val merged = ChainedExchangeRateService(listOf(low, high)).latest()!!

        assertEquals(Rate(1.16).value, merged.rates.getValue(usd).value) // high wins overlap
        assertEquals(Rate(0.00002).value, merged.rates.getValue(btc).value) // low fills the gap
    }

    @Test
    fun `unreachable tiers are skipped`() = runTest {
        val merged = ChainedExchangeRateService(listOf(service(null), service(snapshot(usd to 1.16)))).latest()!!

        assertEquals(Rate(1.16).value, merged.rates.getValue(usd).value)
    }

    @Test
    fun `null when every tier is unreachable`() = runTest {
        assertNull(ChainedExchangeRateService(listOf(service(null), service(null))).latest())
    }
}
