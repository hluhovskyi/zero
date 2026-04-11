package com.hluhovskyi.zero

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hluhovskyi.zero.accounts.AccountEntity
import com.hluhovskyi.zero.accounts.AccountRoom
import com.hluhovskyi.zero.categories.CategoryEntity
import com.hluhovskyi.zero.categories.CategoryRoom
import com.hluhovskyi.zero.config.ConfigurationEntity
import com.hluhovskyi.zero.config.ConfigurationRoom
import com.hluhovskyi.zero.currencies.CurrenciesRoom
import com.hluhovskyi.zero.transactions.TransactionEntity
import com.hluhovskyi.zero.transactions.TransactionRoom
import com.hluhovskyi.zero.users.CurrentUserEntity
import com.hluhovskyi.zero.users.CurrentUserRoom

private const val MAIN_DATABASE_VERSION = 2

@Database(
    entities = [
        CurrentUserEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        ConfigurationEntity::class,
    ],
    version = MAIN_DATABASE_VERSION,
)
@TypeConverters(
    value = [
        IdConverters::class,
        BigDecimalConverters::class,
        LocalDateTimeConverter::class,
    ],
)
internal abstract class MainDatabase : RoomDatabase() {

    abstract fun currentUser(): CurrentUserRoom

    abstract fun account(): AccountRoom

    abstract fun transaction(): TransactionRoom

    abstract fun category(): CategoryRoom

    abstract fun configuration(): ConfigurationRoom

    abstract fun currenciesRoom(): CurrenciesRoom
}
