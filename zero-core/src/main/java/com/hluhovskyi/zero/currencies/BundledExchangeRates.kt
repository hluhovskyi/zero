package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.util.concurrent.atomic.AtomicReference

/** Supplies the offline [ExchangeRateSnapshot] fallback. */
internal interface BundledExchangeRatesSource {
    suspend fun load(): ExchangeRateSnapshot
}

/**
 * Offline exchange-rate fallback, loaded once from two bundled assets and merged into a single
 * EUR-based snapshot:
 *  - [ratesUri] — a Frankfurter response (fiat), the stable baseline;
 *  - [overridesUri] — custom overrides Frankfurter doesn't provide (BTC/ETH), kept for backward
 *    compatibility.
 *
 * Both are EUR-based, so their rate maps merge directly. Live rates override this at runtime.
 */
internal class BundledExchangeRates(
    private val resourceResolver: ResourceResolver,
    private val ratesUri: Uri,
    private val overridesUri: Uri,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : BundledExchangeRatesSource {

    private val cached = AtomicReference<ExchangeRateSnapshot?>(null)

    override suspend fun load(): ExchangeRateSnapshot {
        cached.get()?.let { return it }

        val fiat = read(ratesUri)
        val overrides = read(overridesUri)
        val rates = (fiat?.rates.orEmpty() + overrides?.rates.orEmpty())
            .entries
            .associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) }

        return ExchangeRateSnapshot(
            base = Id((fiat ?: overrides)?.base?.uppercase() ?: DEFAULT_BASE),
            rates = rates,
        ).also(cached::set)
    }

    private suspend fun read(uri: Uri): BundledRates? {
        val status = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull()
            ?: return null
        return status.result.inputStream.use { json.decodeFromStream<BundledRates>(it) }
    }

    @Serializable
    private data class BundledRates(
        val base: String = DEFAULT_BASE,
        val date: String = "",
        val rates: Map<String, Double> = emptyMap(),
    )

    companion object {
        private const val DEFAULT_BASE = "EUR"
    }
}
