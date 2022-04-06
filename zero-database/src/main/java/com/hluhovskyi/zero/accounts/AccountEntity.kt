package com.hluhovskyi.zero.accounts

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id

@Entity(
    indices = [Index("userId")]
)
internal data class AccountEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val currencyId: Id.Known,
    val name: String,
    val iconId: Id.Known,
    @Embedded val initialBalance: AmountEntity,
)