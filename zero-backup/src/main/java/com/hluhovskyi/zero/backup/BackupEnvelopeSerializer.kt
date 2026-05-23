package com.hluhovskyi.zero.backup

import kotlinx.serialization.json.Json

private const val SUPPORTED_FORMAT = 1

class BackupEnvelopeSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun serialize(envelope: BackupEnvelope): String = json.encodeToString(BackupEnvelope.serializer(), envelope)

    fun deserialize(input: String): BackupEnvelope {
        val envelope = json.decodeFromString(BackupEnvelope.serializer(), input)
        check(envelope.format <= SUPPORTED_FORMAT) {
            "Unsupported backup format ${envelope.format}. " +
                "Max supported: $SUPPORTED_FORMAT. Please update Zero."
        }
        return envelope
    }
}
