package com.hluhovskyi.zero.accounts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AccountRoom {

    @Query("SELECT * FROM AccountEntity WHERE userId=:userId")
    fun selectByUserId(userId: String): Flow<List<AccountEntity>>

    fun selectByUserId(userId: Id.Known): Flow<List<AccountEntity>> = selectByUserId(userId.value)

    @Query("SELECT DISTINCT currencyId FROM AccountEntity WHERE userId=:userId")
    fun selectInUseCurrencyIds(userId: String): Flow<List<Id.Known>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(accountEntity: AccountEntity)

    @Transaction
    suspend fun insert(accountEntities: List<AccountEntity>) {
        accountEntities.forEach { insert(it) }
    }
}
