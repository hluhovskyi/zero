package com.hluhovskyi.zero.categories

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRoom {

    fun selectByUserId(userId: Id.Known): Flow<List<CategoryEntity>> = selectByUserId(userId.value)

    @Query("SELECT * FROM CategoryEntity WHERE userId=:userId")
    fun selectByUserId(userId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CategoryEntity)
}