package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

internal class RetrofitExchangeRateService(
    private val service: FrankfurterRemoteService,
) : ExchangeRateService {

    override suspend fun latest(): ExchangeRateSnapshot? = try {
        val response = service.latest()
        ExchangeRateSnapshot(
            base = Id(response.base.uppercase()),
            rates = response.rates.entries.associate { (code, rate) -> Id(code.uppercase()) to Rate(rate) },
        )
    } catch (e: IOException) {
        Timber.w(e, "RetrofitExchangeRateService: network error")
        null
    } catch (e: HttpException) {
        Timber.w(e, "RetrofitExchangeRateService: server returned ${e.code()}")
        null
    }
}
