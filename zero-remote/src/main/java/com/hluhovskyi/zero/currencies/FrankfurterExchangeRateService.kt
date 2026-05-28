package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/** Tier 1: ECB-authoritative rates from Frankfurter (~30 fiat, no crypto). */
internal class FrankfurterExchangeRateService(
    private val service: FrankfurterRemoteService,
) : ExchangeRateService {

    override suspend fun latest(): ExchangeRateSnapshot? = try {
        val response = service.latest()
        ExchangeRateSnapshot(
            base = Id(response.base.uppercase()),
            rates = response.rates.entries.associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) },
        )
    } catch (e: IOException) {
        Timber.w(e, "FrankfurterExchangeRateService: network error")
        null
    } catch (e: HttpException) {
        Timber.w(e, "FrankfurterExchangeRateService: server returned ${e.code()}")
        null
    }
}
