package com.hluhovskyi.zero.transactions

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.RateEntity

@Entity
internal data class TransactionEntity(
    @PrimaryKey val id: Id.Known,
    val type: Type,
    val currencyId: Id.Known,
    val accountId: Id.Known,
    @Embedded(prefix = "amount_") val amount: AmountEntity,
    @Embedded(prefix = "rate_") val rate: RateEntity,
    val targetAccount: String?,
    @Embedded(prefix = "target_amount_") val targetAmount: AmountEntity
) {

    enum class Type {
        EXPENSE,
        INCOME,
        TRANSFER
    }
}