package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    @SerialName("format") val format: Int,
    @SerialName("snapshot") val snapshot: SyncSnapshot,
)
