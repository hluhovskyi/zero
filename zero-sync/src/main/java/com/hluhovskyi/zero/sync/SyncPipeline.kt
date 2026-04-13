package com.hluhovskyi.zero.sync

data class SyncPipeline<T : SyncEntity>(
    val localSource: EntitySyncSource<T>,
    val localSink: EntitySyncSink<T>,
    val resolver: ConflictResolver<T>,
)
