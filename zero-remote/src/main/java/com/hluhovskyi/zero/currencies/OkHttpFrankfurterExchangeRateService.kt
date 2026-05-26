package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

internal class OkHttpFrankfurterExchangeRateService(
    private val endpoint: String,
    private val client: OkHttpClient,
    private val json: Json,
) : ExchangeRateService {

    override suspend fun ratesFor(baseId: Id.Known): Map<Id.Known, Rate> = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) {
            Timber.w("OkHttpFrankfurterExchangeRateService: endpoint not configured")
            return@withContext emptyMap()
        }

        val url = endpoint.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("latest")
            ?.addQueryParameter("base", baseId.value)
            ?.build()
        if (url == null) {
            Timber.w("OkHttpFrankfurterExchangeRateService: invalid endpoint '$endpoint'")
            return@withContext emptyMap()
        }

        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 200) {
                    json.decodeFromString<FrankfurterLatestResponse>(body).rates
                        .entries
                        .associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) }
                } else {
                    Timber.w("OkHttpFrankfurterExchangeRateService: server returned ${response.code}")
                    emptyMap()
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "OkHttpFrankfurterExchangeRateService: network error")
            emptyMap()
        }
    }
}
