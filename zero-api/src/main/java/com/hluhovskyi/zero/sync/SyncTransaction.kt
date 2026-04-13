package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncTransaction(
    @SerialName("id") @Serializable(with = IdKnownSerializer::class) override val id: Id.Known,
    @SerialName("type") val type: Type,
    @SerialName("accountId") @Serializable(with = IdKnownSerializer::class) val accountId: Id.Known,
    @SerialName("currencyId") @Serializable(with = IdKnownSerializer::class) val currencyId: Id.Known,
    @SerialName("categoryId") val categoryId: String?,
    @SerialName("amount") val amount: String,
    @SerialName("rate") val rate: String,
    @SerialName("targetAccountId") val targetAccountId: String?,
    @SerialName("targetAmount") val targetAmount: String?,
    @SerialName("enteredDateTime") val enteredDateTime: LocalDateTime,
    @SerialName("creationDateTime") val creationDateTime: LocalDateTime,
    @SerialName("updatedDateTime") override val updatedDateTime: LocalDateTime,
    @SerialName("deletedAt") override val deletedAt: LocalDateTime?,
) : SyncEntity {

    @Serializable
    enum class Type {
        @SerialName("EXPENSE")
        EXPENSE,

        @SerialName("INCOME")
        INCOME,

        @SerialName("TRANSFER")
        TRANSFER,
    }
}
