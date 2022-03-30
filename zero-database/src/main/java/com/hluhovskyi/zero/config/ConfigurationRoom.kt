package com.hluhovskyi.zero.config

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigurationRoom {

    @Query("SELECT * FROM ConfigurationEntity WHERE name=:name LIMIT 1")
    fun observe(name: String): Flow<ConfigurationEntity>

    @Query("SELECT * FROM ConfigurationEntity WHERE name=:name LIMIT 1")
    fun get(name: String): ConfigurationEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(configuration: ConfigurationEntity)
}