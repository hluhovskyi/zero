package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncSnapshot(
    @SerialName("version") val version: Int,
    @SerialName("userId") @Serializable(with = IdKnownSerializer::class) val userId: Id.Known,
    @SerialName("exportedAt") val exportedAt: LocalDateTime,
    @SerialName("categories") val categories: List<SyncCategory>,
    @SerialName("accounts") val accounts: List<SyncAccount>,
    @SerialName("transactions") val transactions: List<SyncTransaction>,
)
