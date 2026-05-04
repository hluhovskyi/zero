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
    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND datetime(updatedDateTime) > datetime(:after)
          AND deletedAt IS NULL
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    fun selectAfter(userId: String, after: String): Flow<List<TransactionEntity>>

    // One-shot — first page, most recent :limit transactions
    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND deletedAt IS NULL
        ORDER BY datetime(enteredDateTime) DESC
        LIMIT :limit
    """,
    )
    suspend fun selectFirstPage(userId: String, limit: Int): List<TransactionEntity>

    // One-shot — next cursor page, strictly before cursorDate "YYYY-MM-DD"
    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND date(enteredDateTime) < date(:cursorDate)
          AND deletedAt IS NULL
        ORDER BY datetime(enteredDateTime) DESC
        LIMIT :limit
    """,
    )
    suspend fun selectNextPage(userId: String, cursorDate: String, limit: Int): List<TransactionEntity>

    // One-shot — all transactions on :day older than :beforeDateTime (day padding)
    // day: "YYYY-MM-DD", beforeDateTime: ISO datetime string
    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId = :userId
          AND date(enteredDateTime) = date(:day)
          AND datetime(enteredDateTime) < datetime(:beforeDateTime)
          AND deletedAt IS NULL
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    suspend fun selectRemainingOnDay(
        userId: String,
        day: String,
        beforeDateTime: String,
    ): List<TransactionEntity>

    fun selectByUserId(userId: Id.Known): Flow<List<TransactionEntity>> {
        Log.d("GOVNO", "ahuet, ${Thread.currentThread().name}")
        return selectByUserId(userId.value)
    }

    suspend fun selectById(transactionId: Id.Known, userId: Id.Known): TransactionEntity? = selectById(transactionId.value, userId.value)

    @Query("SELECT * FROM TransactionEntity WHERE id=:transactionId AND userId=:userId LIMIT 1")
    suspend fun selectById(transactionId: String, userId: String): TransactionEntity?

    @Query(
        """
        SELECT DISTINCT currencyId FROM TransactionEntity 
        WHERE userId = :userId 
          AND datetime(enteredDateTime) >= datetime(:since)
    """,
    )
    fun selectInUseCurrencyIds(userId: String, since: String): Flow<List<Id.Known>>

    @Query(
        """
        SELECT categoryId,
               COUNT(*) as transactionCount,
               MAX(enteredDateTime) as lastUsedDateTime
        FROM TransactionEntity
        WHERE userId = :userId AND categoryId IS NOT NULL
        GROUP BY categoryId
    """,
    )
    fun selectCategoryUsageStatistic(userId: String): Flow<List<CategoryUsageStatistic>>

    @Query(
        """
        SELECT categoryId,
               currencyId,
               SUM(amount_value) AS totalAmount,
               COUNT(*) AS transactionCount
        FROM TransactionEntity
        WHERE userId = :userId
          AND categoryId IS NOT NULL
          AND type IN ('EXPENSE', 'INCOME')
          AND deletedAt IS NULL
          AND date(enteredDateTime) >= date(:from)
          AND date(enteredDateTime) <= date(:to)
        GROUP BY categoryId, currencyId
    """,
    )
    fun selectCategorySpendingBetween(
        userId: String,
        from: String,
        to: String,
    ): Flow<List<CategorySpendingRow>>

    // Reactive — Room re-emits when any matching row changes.
    // query must already include SQL wildcards, e.g. "%food%". Special chars (%, _) must be pre-escaped.
    @Query(
        """
        SELECT t.* FROM TransactionEntity t
        LEFT JOIN AccountEntity a ON t.accountId = a.id AND a.userId = t.userId
        LEFT JOIN CategoryEntity c ON t.categoryId = c.id AND c.userId = t.userId
        WHERE t.userId = :userId
          AND t.deletedAt IS NULL
          AND (a.name LIKE :query ESCAPE '\' OR c.name LIKE :query ESCAPE '\')
        ORDER BY datetime(t.enteredDateTime) DESC
    """,
    )
    fun search(userId: String, query: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId     = :userId
          AND categoryId = :categoryId
          AND deletedAt  IS NULL
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    fun selectByCategory(userId: String, categoryId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM TransactionEntity
        WHERE userId     = :userId
          AND categoryId = :categoryId
          AND deletedAt  IS NULL
          AND date(enteredDateTime) >= date(:from)
          AND date(enteredDateTime) <= date(:to)
        ORDER BY datetime(enteredDateTime) DESC
    """,
    )
    fun selectByCategoryBetween(
        userId: String,
        categoryId: String,
        from: String,
        to: String,
    ): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)

    @Transaction
    suspend fun insert(entities: List<TransactionEntity>) {
        entities.forEach { insert(it) }
    }

    @Query(
        """
        UPDATE TransactionEntity
        SET deletedAt = :deletedAt, updatedDateTime = :updatedDateTime
        WHERE id = :id AND userId = :userId
    """,
    )
    suspend fun softDelete(id: String, userId: String, deletedAt: String, updatedDateTime: String)
}
