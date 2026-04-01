package com.hluhovskyi.zero.transactions

import android.util.Log
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

    // Reactive — Room re-emits when any matching row is inserted/updated/deleted
    // after: ISO datetime string e.g. "2024-01-15T10:30:00"
    @Query("""
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND datetime(enteredDateTime) > datetime(:after)
        ORDER BY datetime(enteredDateTime) DESC
    """)
    fun selectAfter(userId: String, after: String): Flow<List<TransactionEntity>>

    // One-shot — first page, most recent :limit transactions
    @Query("""
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
        ORDER BY datetime(enteredDateTime) DESC
        LIMIT :limit
    """)
    suspend fun selectFirstPage(userId: String, limit: Int): List<TransactionEntity>

    // One-shot — next cursor page, strictly before cursorDate "YYYY-MM-DD"
    @Query("""
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND date(enteredDateTime) < date(:cursorDate)
        ORDER BY datetime(enteredDateTime) DESC
        LIMIT :limit
    """)
    suspend fun selectNextPage(userId: String, cursorDate: String, limit: Int): List<TransactionEntity>

    // One-shot — all transactions on :day older than :beforeDateTime (day padding)
    // day: "YYYY-MM-DD", beforeDateTime: ISO datetime string
    @Query("""
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND date(enteredDateTime) = date(:day)
          AND datetime(enteredDateTime) < datetime(:beforeDateTime)
        ORDER BY datetime(enteredDateTime) DESC
    """)
    suspend fun selectRemainingOnDay(
        userId: String,
        day: String,
        beforeDateTime: String,
    ): List<TransactionEntity>

    fun selectByUserId(userId: Id.Known): Flow<List<TransactionEntity>> {
        Log.d("GOVNO", "ahuet, ${Thread.currentThread().name}")
        return selectByUserId(userId.value)
    }

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