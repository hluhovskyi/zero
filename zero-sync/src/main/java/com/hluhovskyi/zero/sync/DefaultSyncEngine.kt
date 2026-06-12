package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val SNAPSHOT_VERSION = 1

internal class DefaultSyncEngine(
    private val categoryPipeline: SyncPipeline<SyncCategory>,
    private val accountPipeline: SyncPipeline<SyncAccount>,
    private val transactionPipeline: SyncPipeline<SyncTransaction>,
    private val budgetPipeline: SyncPipeline<SyncBudget>,
    private val resourceResolver: ResourceResolver,
    private val serializer: SyncSerializer,
    private val clock: Clock,
) : SyncEngine {

    override suspend fun export(userId: Id.Known): SyncSnapshot = SyncSnapshot(
        version = SNAPSHOT_VERSION,
        userId = userId,
        exportedAt = clock.now().toLocalDateTime(TimeZone.UTC),
        categories = categoryPipeline.source.exportAll(userId),
        accounts = accountPipeline.source.exportAll(userId),
        transactions = transactionPipeline.source.exportAll(userId),
        budgets = budgetPipeline.source.exportAll(userId),
    )

    override suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot {
        val uriResult = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .first()
            .result

        val json = uriResult.inputStream.bufferedReader().use { it.readText() }
        return serializer.deserialize(json)
    }

    override suspend fun import(snapshot: SyncSnapshot, userId: Id.Known) {
        mergePipeline(categoryPipeline, snapshot.categories, userId)
        mergePipeline(accountPipeline, snapshot.accounts, userId)
        mergePipeline(transactionPipeline, snapshot.transactions, userId)
        mergePipeline(budgetPipeline, snapshot.budgets, userId)
    }

    override suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot = snapshot.copy(
        categories = computeDelta(categoryPipeline, snapshot.categories, userId),
        accounts = computeDelta(accountPipeline, snapshot.accounts, userId),
        transactions = computeDelta(transactionPipeline, snapshot.transactions, userId),
        budgets = computeDelta(budgetPipeline, snapshot.budgets, userId),
    )

    override suspend fun lastModifiedAt(userId: Id.Known): LocalDateTime? = sequenceOf(
        categoryPipeline.source.lastModifiedAt(userId),
        accountPipeline.source.lastModifiedAt(userId),
        transactionPipeline.source.lastModifiedAt(userId),
        budgetPipeline.source.lastModifiedAt(userId),
    ).filterNotNull().maxOrNull()

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
