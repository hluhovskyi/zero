package com.hluhovskyi.zero.transactions

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.RateEntity
import kotlinx.datetime.LocalDateTime

@Entity(
    // Composite covers userId lookups and the selectWindow range scan.
    indices = [Index("userId", "enteredDateTime")],
)
internal data class TransactionEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val type: Type,
    val currencyId: Id.Known,
    val accountId: Id.Known,
    val categoryId: String?,
    @Embedded(prefix = "amount_") val amount: AmountEntity,
    @Embedded(prefix = "rate_") val rate: RateEntity,
    val targetAccount: String?,
    @Embedded(prefix = "target_amount_") val targetAmount: AmountEntity,
    val enteredDateTime: LocalDateTime,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
    val notes: String? = null,
) {
    enum class Type {
        EXPENSE,
        INCOME,
        TRANSFER,
    }
}
