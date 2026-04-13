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
        categories = categoryPipeline.localSource.exportAll(userId),
        accounts = accountPipeline.localSource.exportAll(userId),
        transactions = transactionPipeline.localSource.exportAll(userId),
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
        val local = pipeline.localSource.exportAll(userId).associateBy { it.id }
        val toUpsert = incoming.mapNotNull { entity ->
            val winner = pipeline.resolver.resolve(local[entity.id], entity).firstOrNull() ?: return@mapNotNull null
            // Only write if winner came from incoming (i.e., it's not identical to the local version already stored)
            if (winner == local[entity.id]) null else winner
        }
        if (toUpsert.isNotEmpty()) pipeline.localSink.syncUpsert(toUpsert)
    }
}
