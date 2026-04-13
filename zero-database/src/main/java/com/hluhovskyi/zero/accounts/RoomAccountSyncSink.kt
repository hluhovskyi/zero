package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSink
import com.hluhovskyi.zero.sync.SyncAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

internal class RoomAccountSyncSink(
    private val dao: () -> AccountSyncDao,
    private val currentUserId: Flow<Id.Known>,
) : EntitySyncSink<SyncAccount> {

    override suspend fun syncUpsert(entities: List<SyncAccount>) {
        val userId = currentUserId.first()
        dao().syncUpsert(entities.map { it.toEntity(userId) })
    }

    private fun SyncAccount.toEntity(userId: Id.Known) = AccountEntity(
        id = id,
        userId = userId,
        currencyId = currencyId,
        name = name,
        iconId = iconId,
        initialBalance = AmountEntity(BigDecimal(initialBalance)),
        category = category,
        details = details,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
