package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/** Tier 2: broad coverage (200+ fiat + crypto) from the community currency-api; EUR-based. */
internal class CurrencyApiExchangeRateService(
    private val service: CurrencyApiRemoteService,
) : ExchangeRateService {

    override suspend fun latest(): ExchangeRateSnapshot? = try {
        val response = service.latest()
        ExchangeRateSnapshot(
            base = Id(BASE),
            rates = response.eur.entries.associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) },
        )
    } catch (e: IOException) {
        Timber.w(e, "CurrencyApiExchangeRateService: network error")
        null
    } catch (e: HttpException) {
        Timber.w(e, "CurrencyApiExchangeRateService: server returned ${e.code()}")
        null
    }

    companion object {
        private const val BASE = "EUR"
    }
}
