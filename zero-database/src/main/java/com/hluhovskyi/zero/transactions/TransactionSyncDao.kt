package com.hluhovskyi.zero.transactions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Dao
internal interface TransactionSyncDao {

    @Query("SELECT * FROM TransactionEntity WHERE userId = :userId")
    suspend fun selectAllForSync(userId: Id.Known): List<TransactionEntity>

    @Query("SELECT * FROM TransactionEntity WHERE userId = :userId AND updatedDateTime > :since")
    suspend fun selectSince(userId: Id.Known, since: LocalDateTime): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun syncUpsert(entities: List<TransactionEntity>)
}
