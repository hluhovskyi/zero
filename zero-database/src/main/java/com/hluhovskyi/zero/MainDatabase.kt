package com.hluhovskyi.zero

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hluhovskyi.zero.transactions.TransactionEntity
import com.hluhovskyi.zero.transactions.TransactionRoom
import com.hluhovskyi.zero.users.CurrentUserEntity
import com.hluhovskyi.zero.users.CurrentUserRoom

private const val MAIN_DATABASE_VERSION = 1

@Database(
    entities = [
        CurrentUserEntity::class,

        TransactionEntity::class
    ],
    version = MAIN_DATABASE_VERSION
)
@TypeConverters(
    value = [
        IdConverters::class,
        BigDecimalConverters::class
    ]
)
internal abstract class MainDatabase : RoomDatabase() {

    abstract fun currentUser(): CurrentUserRoom

    abstract fun transaction(): TransactionRoom
}