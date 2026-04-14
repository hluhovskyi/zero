package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val SNAPSHOT_VERSION = 1

internal class DefaultSyncEngine(
    private val categoryPipeline: SyncPipeline<SyncCategory>,
    private val accountPipeline: SyncPipeline<SyncAccount>,
    private val transactionPipeline: SyncPipeline<SyncTransaction>,
    private val resourceResolver: ResourceResolver,
    private val serializer: SyncSerializer,
) : SyncEngine {

    override suspend fun export(userId: Id.Known): SyncSnapshot = SyncSnapshot(
        version = SNAPSHOT_VERSION,
        userId = userId,
        exportedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        categories = categoryPipeline.source.exportAll(userId),
        accounts = accountPipeline.source.exportAll(userId),
        transactions = transactionPipeline.source.exportAll(userId),
    )

    override suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot {
        var uriResult: com.hluhovskyi.zero.resource.UriResult? = null
        resourceResolver.resolve(UriRequest(uri)).collect { status ->
            if (status is ResourceStatus.Result<*>) {
                @Suppress("UNCHECKED_CAST")
                uriResult = (status as ResourceStatus.Result<com.hluhovskyi.zero.resource.UriResult>).result
            }
        }
        val json = uriResult?.inputStream?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read file: $uri")
        return serializer.deserialize(json)
    }

    override suspend fun import(snapshot: SyncSnapshot, userId: Id.Known) {
        mergePipeline(categoryPipeline, snapshot.categories, userId)
        mergePipeline(accountPipeline, snapshot.accounts, userId)
        mergePipeline(transactionPipeline, snapshot.transactions, userId)
    }

    override suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot = snapshot.copy(
        categories = computeDelta(categoryPipeline, snapshot.categories, userId),
        accounts = computeDelta(accountPipeline, snapshot.accounts, userId),
        transactions = computeDelta(transactionPipeline, snapshot.transactions, userId),
    )

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

    private suspend fun <T : SyncEntity> computeDelta(
        pipeline: SyncPipeline<T>,
        incoming: List<T>,
        userId: Id.Known,
    ): List<T> {
        val stored = pipeline.source.exportAll(userId).associateBy { it.id }
        return incoming.flatMap { entity ->
            pipeline.resolver.resolve(stored[entity.id], entity)
                .filter { it != stored[entity.id] }
        }
    }
}
