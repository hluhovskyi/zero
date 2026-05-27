package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.firstOrDefault
import com.hluhovskyi.zero.config.write
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persists the most recent exchange-rate snapshot so live rates survive app restarts. Backed by
 * [ConfigurationRepository] (Room/SQLite): writes are atomic and durable, so there is no torn-write
 * window to guard against. Stores the local day it was fetched on; the loader uses that to fetch at
 * most once per calendar day. A blank or corrupt value reads back as `null`, triggering a refetch.
 */
internal class RateSnapshotStore(
    private val configurationRepository: ConfigurationRepository,
    // Same setup as SyncSerializer / BackupEnvelopeSerializer; kotlinx.datetime types serialize as
    // ISO-8601 via their built-in serializers, no SerializersModule needed.
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {

    suspend fun load(): Stored? {
        val raw = configurationRepository.firstOrDefault(CurrencyConfigurationKey.RateSnapshot)
        if (raw.isBlank()) {
            return null
        }
        return try {
            json.decodeFromString(Stored.serializer(), raw)
        } catch (e: SerializationException) {
            Timber.w(e, "RateSnapshotStore: corrupt rate snapshot")
            null
        }
    }

    suspend fun save(stored: Stored) {
        configurationRepository.write(
            CurrencyConfigurationKey.RateSnapshot,
            json.encodeToString(Stored.serializer(), stored),
        )
    }

    @Serializable
    data class Stored(
        val fetchedOn: LocalDate,
        val base: String,
        val rates: Map<String, Double>,
    )
}
