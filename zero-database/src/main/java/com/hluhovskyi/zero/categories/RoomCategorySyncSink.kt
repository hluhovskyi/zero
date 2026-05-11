package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSink
import com.hluhovskyi.zero.sync.SyncCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal class RoomCategorySyncSink(
    private val dao: () -> CategorySyncDao,
    private val currentUserId: Flow<Id.Known>,
) : EntitySyncSink<SyncCategory> {

    override suspend fun syncUpsert(entities: List<SyncCategory>) {
        val userId = currentUserId.first()
        dao().syncUpsert(entities.map { it.toEntity(userId) })
    }

    private fun SyncCategory.toEntity(userId: Id.Known) = CategoryEntity(
        id = id,
        userId = userId,
        name = name,
        iconId = iconId,
        colorId = colorId,
        type = type ?: CategoryType.EXPENSE.name,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
