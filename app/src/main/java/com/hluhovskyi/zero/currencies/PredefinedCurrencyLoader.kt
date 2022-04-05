package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.LocaleProvider
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class PredefinedCurrencyLoader(
    private val resourceResolver: ResourceResolver,
    private val androidUriResourceFactory: AndroidUriResourceFactory,
    private val localeProvider: LocaleProvider,
    logger: Logger,
) : CurrencyLoader {

    private val logger = logger.withTag("PredefinedCurrencyLoader")

    private val availableCurrencies = AtomicReference<Set<Id.Known>>(emptySet())
    private val currencyRates = ConcurrentHashMap<Id.Known, Map<Id.Known, Rate>>()

    override suspend fun availableCurrencies(): Set<Id.Known> {
        return availableCurrencies.get().ifEmpty {
            val uri = androidUriResourceFactory.asset("currencies.min.json")
            val status = resourceResolver.resolve(UriRequest(uri))
                .filterIsInstance<ResourceStatus.Result<UriResult>>()
                .firstOrNull()

            if (status == null) {
                emptySet()
            } else {
                status.result.inputStream
                    .use { stream ->
                        Json.decodeFromStream<Map<String, String>>(stream).let {
                            it.keys.mapTo(LinkedHashSet(it.size)) { id -> Id(id.uppercase(localeProvider.locale())) }
                        }
                    }
                    .also { logger.d("availableCurrencies, ids=$it") }
                    .also(availableCurrencies::set)
            }
        }
    }

    override suspend fun ratesFor(currencyId: Id.Known): Map<Id.Known, Rate> {
        val cachedRates = currencyRates[currencyId]
        if (cachedRates != null) {
            return cachedRates
        }

        val locale = localeProvider.locale()
        val currencyName = currencyId.value.lowercase(locale)
        val uri = androidUriResourceFactory.asset("currencies/$currencyName.min.json")
        val status = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull()

        return if (status == null) {
            emptyMap()
        } else {
            status.result.inputStream.use { stream ->
                Json.decodeFromStream<Map<String, JsonElement>>(stream).let {
                    it[currencyName]?.jsonObject?.entries.orEmpty()
                        .map { (id, rate) -> Id(id.uppercase(locale)) to Rate(rate.jsonPrimitive.double) }
                        .toMap()
                }
            }
        }
    }
}