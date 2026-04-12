package com.hluhovskyi.zero.sync

interface EntitySyncSink<T : SyncEntity> {
    /**
     * Upsert entities exactly as-is, preserving all timestamps and IDs.
     * Must NOT overwrite updatedDateTime or generate new IDs.
     */
    suspend fun syncUpsert(entities: List<T>)
}
