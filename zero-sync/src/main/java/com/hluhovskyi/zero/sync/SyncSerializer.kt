package com.hluhovskyi.zero.sync

import kotlinx.serialization.json.Json

private const val SUPPORTED_VERSION = 1

class SyncSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true // forward-compat: ignore fields added in future versions
    }

    fun serialize(snapshot: SyncSnapshot): String = json.encodeToString(SyncSnapshot.serializer(), snapshot)

    fun deserialize(input: String): SyncSnapshot {
        val snapshot = json.decodeFromString(SyncSnapshot.serializer(), input)
        check(snapshot.version <= SUPPORTED_VERSION) {
            "Unsupported sync format version ${snapshot.version}. " +
                "Max supported: $SUPPORTED_VERSION. Please update the app."
        }
        return snapshot
    }
}
