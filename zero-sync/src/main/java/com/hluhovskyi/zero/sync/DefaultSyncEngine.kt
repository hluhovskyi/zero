package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val SNAPSHOT_VERSION = 1

internal class DefaultSyncEngine(
    private val categoryPipeline: SyncPipeline<SyncCategory>,
    private val accountPipeline: SyncPipeline<SyncAccount>,
    private val transactionPipeline: SyncPipeline<SyncTransaction>,
) : SyncEngine {

    override suspend fun export(userId: Id.Known): SyncSnapshot = SyncSnapshot(
        version = SNAPSHOT_VERSION,
        userId = userId,
        exportedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        categories = categoryPipeline.source.exportAll(userId),
        accounts = accountPipeline.source.exportAll(userId),
        transactions = transactionPipeline.source.exportAll(userId),
    )

    override suspend fun import(snapshot: SyncSnapshot, userId: Id.Known) {
        // Processing order: categories → accounts → transactions (dependency order)
        mergePipeline(categoryPipeline, snapshot.categories, userId)
        mergePipeline(accountPipeline, snapshot.accounts, userId)
        mergePipeline(transactionPipeline, snapshot.transactions, userId)
    }

    private suspend fun <T : SyncEntity> mergePipeline(
        pipeline: SyncPipeline<T>,
        incoming: List<T>,
        userId: Id.Known,
    ) {
        val stored = pipeline.source.exportAll(userId).associateBy { it.id }
        val toUpsert = incoming.flatMap { entity ->
            val storedEntity = stored[entity.id]
            pipeline.resolver.resolve(storedEntity, entity)
                .filter { winner -> winner != storedEntity }
        }
        if (toUpsert.isNotEmpty()) pipeline.sink.syncUpsert(toUpsert)
    }
}
