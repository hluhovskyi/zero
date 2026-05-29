package com.hluhovskyi.zero.budget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Dao
internal interface BudgetSyncDao {

    @Query("SELECT * FROM BudgetEntity WHERE userId = :userId")
    suspend fun selectAllForSync(userId: Id.Known): List<BudgetEntity>

    @Query("SELECT * FROM BudgetEntity WHERE userId = :userId AND updatedDateTime > :since")
    suspend fun selectSince(userId: Id.Known, since: LocalDateTime): List<BudgetEntity>

    @Query("SELECT MAX(updatedDateTime) FROM BudgetEntity WHERE userId = :userId")
    suspend fun selectLastModifiedAt(userId: Id.Known): LocalDateTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun syncUpsert(entities: List<BudgetEntity>)
}
