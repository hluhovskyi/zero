package com.hluhovskyi.zero.currencies

import kotlinx.serialization.Serializable

/**
 * Response shape of the `@fawazahmed0/currency-api` EUR endpoint: `{"date": "...", "eur": {...}}`.
 * Only the `eur` table is consumed (we always request the EUR base).
 */
@Serializable
internal data class CurrencyApiResponse(
    val date: String = "",
    val eur: Map<String, Double> = emptyMap(),
)
