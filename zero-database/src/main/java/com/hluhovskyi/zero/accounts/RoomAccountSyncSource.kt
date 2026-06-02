package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSource
import com.hluhovskyi.zero.sync.SyncAccount
import kotlinx.datetime.LocalDateTime

internal class RoomAccountSyncSource(
    private val dao: () -> AccountSyncDao,
) : EntitySyncSource<SyncAccount> {

    override suspend fun exportAll(userId: Id.Known): List<SyncAccount> = dao().selectAllForSync(userId).map { it.toSyncModel() }

    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<SyncAccount> = dao().selectSince(userId, since).map { it.toSyncModel() }

    override suspend fun lastModifiedAt(userId: Id.Known): LocalDateTime? = dao().selectLastModifiedAt(userId)

    private fun AccountEntity.toSyncModel() = SyncAccount(
        id = id,
        currencyId = currencyId,
        name = name,
        iconId = iconId,
        initialBalance = initialBalance.value.toPlainString(),
        category = category,
        details = details,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
