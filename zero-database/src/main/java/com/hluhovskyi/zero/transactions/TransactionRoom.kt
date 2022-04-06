package com.hluhovskyi.zero.transactions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TransactionRoom {

    @Query("SELECT * FROM TransactionEntity WHERE userId=:userId ORDER BY datetime(enteredDateTime) DESC")
    fun selectByUserId(userId: String): Flow<List<TransactionEntity>>

    fun selectByUserId(userId: Id.Known): Flow<List<TransactionEntity>> =
        selectByUserId(userId.value)

    suspend fun selectById(transactionId: Id.Known, userId: Id.Known): TransactionEntity? =
        selectById(transactionId.value, userId.value)

    @Query("SELECT * FROM TransactionEntity WHERE id=:transactionId AND userId=:userId LIMIT 1")
    suspend fun selectById(transactionId: String, userId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)

    @Transaction
    suspend fun insert(entities: List<TransactionEntity>) {
        entities.forEach { insert(it) }
    }
}