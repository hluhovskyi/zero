package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSource
import com.hluhovskyi.zero.sync.SyncBudget
import kotlinx.datetime.LocalDateTime

internal class RoomBudgetSyncSource(
    private val dao: () -> BudgetSyncDao,
) : EntitySyncSource<SyncBudget> {

    override suspend fun exportAll(userId: Id.Known): List<SyncBudget> =
        dao().selectAllForSync(userId).map { it.toSyncModel() }

    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<SyncBudget> =
        dao().selectSince(userId, since).map { it.toSyncModel() }

    private fun BudgetEntity.toSyncModel() = SyncBudget(
        id = id,
        categoryId = categoryId,
        type = type,
        amount = amount.toPlainString(),
        periodStart = periodStart,
        periodEnd = periodEnd,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
