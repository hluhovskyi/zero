package com.hluhovskyi.zero.transactions

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.RateEntity
import java.time.LocalDateTime

@Entity(
    indices = [Index("userId")]
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
) {

    enum class Type {
        EXPENSE,
        INCOME,
        TRANSFER
    }
}