package com.hluhovskyi.zero.currencies

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Persists the most recent exchange-rate snapshot to a JSON file so live rates survive app
 * restarts. Stores the local day it was fetched on; the loader uses that to fetch at most once
 * per calendar day. All failures are swallowed — a missing or corrupt cache simply means a refetch.
 */
internal class RateSnapshotStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    suspend fun load(): Stored? = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext null
        }
        try {
            json.decodeFromString(Stored.serializer(), file.readText())
        } catch (e: IOException) {
            Timber.w(e, "RateSnapshotStore: failed to read $FILE_NAME")
            null
        } catch (e: SerializationException) {
            Timber.w(e, "RateSnapshotStore: corrupt $FILE_NAME")
            null
        }
    }

    suspend fun save(stored: Stored) = withContext(Dispatchers.IO) {
        try {
            file.writeText(json.encodeToString(Stored.serializer(), stored))
        } catch (e: IOException) {
            Timber.w(e, "RateSnapshotStore: failed to write $FILE_NAME")
        }
    }

    @Serializable
    data class Stored(
        val fetchedOn: String,
        val base: String,
        val rates: Map<String, Double>,
    )

    companion object {
        private const val FILE_NAME = "exchange_rates.json"
    }
}
