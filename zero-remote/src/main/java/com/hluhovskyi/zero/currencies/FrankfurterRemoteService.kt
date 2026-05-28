package com.hluhovskyi.zero.currencies

import retrofit2.http.GET
import retrofit2.http.Query

internal interface FrankfurterRemoteService {

    @GET("v1/latest")
    suspend fun latest(@Query("base") base: String = DEFAULT_BASE): FrankfurterLatestResponse

    companion object {
        /** ECB's native base. One call relative to it covers every fiat pair via cross-rates. */
        const val DEFAULT_BASE = "EUR"
    }
}
