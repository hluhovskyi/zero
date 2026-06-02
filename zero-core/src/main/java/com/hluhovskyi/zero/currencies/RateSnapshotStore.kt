package com.hluhovskyi.zero.currencies

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Persists the latest exchange-rate snapshot so live rates survive app restarts. */
internal interface RateSnapshotStore {

    suspend fun load(): Stored?

    suspend fun save(stored: Stored)

    @Serializable
    data class Stored(
        val fetchedOn: LocalDate,
        val base: String,
        val rates: Map<String, Double>,
    )
}
