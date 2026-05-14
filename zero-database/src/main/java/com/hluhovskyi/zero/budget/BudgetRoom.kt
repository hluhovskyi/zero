package com.hluhovskyi.zero.budget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Dao
internal interface BudgetRoom {

    fun selectByUserId(userId: Id.Known): Flow<List<BudgetEntity>> = selectByUserId(userId.value)

    fun selectForPeriod(userId: Id.Known, from: LocalDate, to: LocalDate, type: String): Flow<List<BudgetEntity>> =
        selectForPeriod(userId.value, from.toString(), to.toString(), type)

    fun selectForCategoryAndPeriod(
        userId: Id.Known,
        categoryId: Id.Known,
        from: LocalDate,
        to: LocalDate,
        type: String,
    ): Flow<BudgetEntity?> = selectForCategoryAndPeriod(userId.value, categoryId.value, from.toString(), to.toString(), type)

    fun selectHasAnyForPeriod(userId: Id.Known, from: LocalDate, to: LocalDate, type: String): Flow<Boolean> =
        selectHasAnyForPeriod(userId.value, from.toString(), to.toString(), type)

    @Query(
        """
        SELECT * FROM BudgetEntity
        WHERE userId = :userId AND deletedAt IS NULL
        ORDER BY datetime(creationDateTime) DESC
    """,
    )
    fun selectByUserId(userId: String): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM BudgetEntity
        WHERE userId = :userId
          AND periodStart = :from
          AND periodEnd = :to
          AND type = :type
          AND deletedAt IS NULL
    """,
    )
    fun selectForPeriod(userId: String, from: String, to: String, type: String): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM BudgetEntity
        WHERE userId = :userId
          AND categoryId = :categoryId
          AND periodStart = :from
          AND periodEnd = :to
          AND type = :type
          AND deletedAt IS NULL
        LIMIT 1
    """,
    )
    fun selectForCategoryAndPeriod(
        userId: String,
        categoryId: String,
        from: String,
        to: String,
        type: String,
    ): Flow<BudgetEntity?>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM BudgetEntity
            WHERE userId = :userId
              AND periodStart = :from
              AND periodEnd = :to
              AND type = :type
              AND deletedAt IS NULL
            LIMIT 1
        )
    """,
    )
    fun selectHasAnyForPeriod(userId: String, from: String, to: String, type: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudgetEntity)

    @Transaction
    suspend fun insert(entities: List<BudgetEntity>) {
        entities.forEach { insert(it) }
    }

    suspend fun softDelete(id: Id.Known, userId: Id.Known, updatedDateTime: LocalDateTime) =
        softDelete(id.value, userId.value, updatedDateTime.toString())

    @Query(
        """
        UPDATE BudgetEntity
        SET deletedAt = :updatedDateTime, updatedDateTime = :updatedDateTime
        WHERE id = :id AND userId = :userId
    """,
    )
    suspend fun softDelete(id: String, userId: String, updatedDateTime: String)
}
