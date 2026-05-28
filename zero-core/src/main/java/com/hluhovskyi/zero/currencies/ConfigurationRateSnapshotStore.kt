package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.firstOrDefault
import com.hluhovskyi.zero.config.write
import com.hluhovskyi.zero.currencies.RateSnapshotStore.Stored
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * [RateSnapshotStore] backed by [ConfigurationRepository] (Room/SQLite): writes are atomic and
 * durable, and a blank or corrupt value reads back as `null` so the loader refetches.
 */
internal class ConfigurationRateSnapshotStore(
    private val configurationRepository: ConfigurationRepository,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : RateSnapshotStore {

    override suspend fun load(): Stored? {
        val raw = configurationRepository.firstOrDefault(CurrencyConfigurationKey.RateSnapshot)
        if (raw.isBlank()) {
            return null
        }
        return try {
            json.decodeFromString(Stored.serializer(), raw)
        } catch (e: SerializationException) {
            Timber.w(e, "ConfigurationRateSnapshotStore: corrupt rate snapshot")
            null
        }
    }

    override suspend fun save(stored: Stored) {
        configurationRepository.write(
            CurrencyConfigurationKey.RateSnapshot,
            json.encodeToString(Stored.serializer(), stored),
        )
    }
}
