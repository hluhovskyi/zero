package com.hluhovskyi.zero.currencies

import androidx.room.Dao
import androidx.room.Query
import com.hluhovskyi.zero.common.Id

@Dao
interface CurrenciesRoom {

    @Query("SELECT DISTINCT currencyId FROM AccountEntity")
    suspend fun query(): List<Id.Known>
}
