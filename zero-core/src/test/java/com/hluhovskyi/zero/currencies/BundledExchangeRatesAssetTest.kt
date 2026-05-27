package com.hluhovskyi.zero.currencies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the real shipped exchange-rate assets so the supported currency set cannot silently change
 * or break version to version. Reads the actual files from `src/main/assets`.
 */
class BundledExchangeRatesAssetTest {

    @Serializable
    private data class Rates(val base: String = "", val rates: Map<String, Double> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): Rates {
        val file = File("src/main/assets/$name")
        assertTrue("missing bundled asset: ${file.absolutePath}", file.exists())
        return json.decodeFromString(Rates.serializer(), file.readText())
    }

    @Test
    fun `bundled supported currency set is stable`() {
        val fiat = load("exchange_rates.min.json")
        val overrides = load("exchange_rate_overrides.min.json")

        val supported = buildSet {
            add(fiat.base)
            addAll(fiat.rates.keys)
            addAll(overrides.rates.keys)
        }

        // 29 Frankfurter fiat rates + EUR base + BTC + ETH overrides. Bump deliberately if the
        // bundled files change — this is the version-to-version stability guard.
        assertEquals(32, supported.size)
        assertEquals("EUR", fiat.base)
        assertTrue("USD" in supported)
        assertTrue("BTC" in supported && "ETH" in supported)
    }
}
