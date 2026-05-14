package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncBudget(
    @SerialName("id") @Serializable(with = IdKnownSerializer::class) override val id: Id.Known,
    @SerialName("categoryId") @Serializable(with = IdKnownSerializer::class) val categoryId: Id.Known,
    @SerialName("type") val type: String,
    @SerialName("amount") val amount: String,
    @SerialName("periodStart") val periodStart: LocalDate,
    @SerialName("periodEnd") val periodEnd: LocalDate,
    @SerialName("creationDateTime") val creationDateTime: LocalDateTime,
    @SerialName("updatedDateTime") override val updatedDateTime: LocalDateTime,
    @SerialName("deletedAt") override val deletedAt: LocalDateTime?,
) : SyncEntity
