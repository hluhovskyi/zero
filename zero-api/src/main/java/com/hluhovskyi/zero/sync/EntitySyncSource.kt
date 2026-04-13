package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

interface EntitySyncSource<T : SyncEntity> {
    /** All entities including tombstones. Used for full export. */
    suspend fun exportAll(userId: Id.Known): List<T>

    /** Entities with updatedDateTime > [since]. Used for delta sync. */
    suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<T>
}
