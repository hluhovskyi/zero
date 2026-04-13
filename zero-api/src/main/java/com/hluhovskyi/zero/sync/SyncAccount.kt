package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncAccount(
    @SerialName("id") @Serializable(with = IdKnownSerializer::class) override val id: Id.Known,
    @SerialName("currencyId") @Serializable(with = IdKnownSerializer::class) val currencyId: Id.Known,
    @SerialName("name") val name: String,
    @SerialName("iconId") @Serializable(with = IdKnownSerializer::class) val iconId: Id.Known,
    @SerialName("initialBalance") val initialBalance: String,
    @SerialName("category") val category: String,
    @SerialName("details") val details: String?,
    @SerialName("creationDateTime") val creationDateTime: LocalDateTime,
    @SerialName("updatedDateTime") override val updatedDateTime: LocalDateTime,
    @SerialName("deletedAt") override val deletedAt: LocalDateTime?,
) : SyncEntity
