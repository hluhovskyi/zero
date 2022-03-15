package com.hluhovskyi.zero.users

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface CurrentUserRoom {

    @Query("SELECT * FROM CurrentUserEntity LIMIT 1")
    suspend fun selectFirst(): CurrentUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userEntity: CurrentUserEntity)
}