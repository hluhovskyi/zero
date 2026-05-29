package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSource
import com.hluhovskyi.zero.sync.SyncCategory
import kotlinx.datetime.LocalDateTime

internal class RoomCategorySyncSource(
    private val dao: () -> CategorySyncDao,
) : EntitySyncSource<SyncCategory> {

    override suspend fun exportAll(userId: Id.Known): List<SyncCategory> = dao().selectAllForSync(userId).map { it.toSyncModel() }

    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<SyncCategory> = dao().selectSince(userId, since).map { it.toSyncModel() }

    override suspend fun lastModifiedAt(userId: Id.Known): LocalDateTime? = dao().selectLastModifiedAt(userId)

    private fun CategoryEntity.toSyncModel() = SyncCategory(
        id = id,
        name = name,
        iconId = iconId,
        colorId = colorId,
        parentCategoryId = null,
        type = type,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
