package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncCategory(
    @SerialName("id") @Serializable(with = IdKnownSerializer::class) override val id: Id.Known,
    @SerialName("name") val name: String,
    @SerialName("iconId") val iconId: String?,
    @SerialName("colorId") val colorId: String?,
    @SerialName("parentCategoryId") val parentCategoryId: String?,
    @SerialName("type") val type: String? = null,
    @SerialName("creationDateTime") val creationDateTime: LocalDateTime,
    @SerialName("updatedDateTime") override val updatedDateTime: LocalDateTime,
    @SerialName("deletedAt") override val deletedAt: LocalDateTime?,
) : SyncEntity
