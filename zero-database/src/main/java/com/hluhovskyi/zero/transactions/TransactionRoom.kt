package com.hluhovskyi.zero.transactions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TransactionRoom {

    @Query("SELECT * FROM TransactionEntity WHERE userId=:userId")
    fun selectByUserId(userId: String): Flow<List<TransactionEntity>>

    fun selectByUserId(userId: Id.Known): Flow<List<TransactionEntity>> =
        selectByUserId(userId.value)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)
}