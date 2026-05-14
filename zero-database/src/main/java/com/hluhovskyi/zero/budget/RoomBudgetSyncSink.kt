package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSink
import com.hluhovskyi.zero.sync.SyncBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

internal class RoomBudgetSyncSink(
    private val dao: () -> BudgetSyncDao,
    private val currentUserId: Flow<Id.Known>,
) : EntitySyncSink<SyncBudget> {

    override suspend fun syncUpsert(entities: List<SyncBudget>) {
        val userId = currentUserId.first()
        dao().syncUpsert(entities.map { it.toEntity(userId) })
    }

    private fun SyncBudget.toEntity(userId: Id.Known) = BudgetEntity(
        id = id,
        userId = userId,
        categoryId = categoryId,
        type = BudgetType.from(type).name,
        amount = BigDecimal(amount),
        periodStart = periodStart,
        periodEnd = periodEnd,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
