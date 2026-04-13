package com.hluhovskyi.zero.accounts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Dao
internal interface AccountSyncDao {

    @Query("SELECT * FROM AccountEntity WHERE userId = :userId")
    suspend fun selectAllForSync(userId: Id.Known): List<AccountEntity>

    @Query("SELECT * FROM AccountEntity WHERE userId = :userId AND updatedDateTime > :since")
    suspend fun selectSince(userId: Id.Known, since: LocalDateTime): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun syncUpsert(entities: List<AccountEntity>)
}
