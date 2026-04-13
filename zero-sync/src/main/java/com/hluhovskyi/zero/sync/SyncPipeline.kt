package com.hluhovskyi.zero.sync

class SyncPipeline<T : SyncEntity>(
    val source: EntitySyncSource<T>,
    val sink: EntitySyncSink<T>,
    val resolver: ConflictResolver<T>,
)
