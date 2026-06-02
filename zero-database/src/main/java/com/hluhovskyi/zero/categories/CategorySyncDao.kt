package com.hluhovskyi.zero.categories

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Dao
internal interface CategorySyncDao {

    @Query("SELECT * FROM CategoryEntity WHERE userId = :userId")
    suspend fun selectAllForSync(userId: Id.Known): List<CategoryEntity>

    @Query("SELECT * FROM CategoryEntity WHERE userId = :userId AND updatedDateTime > :since")
    suspend fun selectSince(userId: Id.Known, since: LocalDateTime): List<CategoryEntity>

    @Query("SELECT MAX(updatedDateTime) FROM CategoryEntity WHERE userId = :userId")
    suspend fun selectLastModifiedAt(userId: Id.Known): LocalDateTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun syncUpsert(entities: List<CategoryEntity>)
}
