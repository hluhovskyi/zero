package com.hluhovskyi.zero.backup

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    @SerialName("backupId") val backupId: String,
    @SerialName("createdAt") val createdAt: LocalDateTime,
    @SerialName("byteSize") val byteSize: Long,
    @SerialName("deviceLabel") val deviceLabel: String,
)
