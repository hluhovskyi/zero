package com.hluhovskyi.zero.currencies

import retrofit2.http.GET

internal interface CurrencyApiRemoteService {

    /** EUR-based table covering 200+ fiat currencies and crypto. */
    @GET("v1/currencies/eur.min.json")
    suspend fun latest(): CurrencyApiResponse
}
