package com.hluhovskyi.zero.categories

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRoom {

    fun selectByUserId(userId: Id.Known): Flow<List<CategoryEntity>> = selectByUserId(userId.value)

    fun selectById(id: Id.Known, userId: Id.Known): Flow<CategoryEntity> = selectById(id.value, userId.value)

    @Query("SELECT * FROM CategoryEntity WHERE userId=:userId")
    fun selectByUserId(userId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM CategoryEntity WHERE userId=:userId AND id=:id LIMIT 1")
    fun selectById(id: String, userId: String): Flow<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CategoryEntity)

    @Transaction
    suspend fun insert(entities: List<CategoryEntity>) {
        entities.forEach { insert(it) }
    }
}