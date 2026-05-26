package com.hluhovskyi.zero.currencies

import kotlinx.serialization.Serializable

@Serializable
internal data class FrankfurterLatestResponse(
    val base: String = "",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap(),
)
