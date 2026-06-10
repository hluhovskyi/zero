package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.resource.ResourceResolver

interface SyncComponent {

    interface Dependencies {
        val categorySyncSource: EntitySyncSource<SyncCategory>
        val categorySyncSink: EntitySyncSink<SyncCategory>
        val accountSyncSource: EntitySyncSource<SyncAccount>
        val accountSyncSink: EntitySyncSink<SyncAccount>
        val transactionSyncSource: EntitySyncSource<SyncTransaction>
        val transactionSyncSink: EntitySyncSink<SyncTransaction>
        val budgetSyncSource: EntitySyncSource<SyncBudget>
        val budgetSyncSink: EntitySyncSink<SyncBudget>
        val resourceResolver: ResourceResolver
        val clock: Clock
    }

    val syncEngine: SyncEngine
    val serializer: SyncSerializer

    class Factory(private val dependencies: Dependencies) {
        fun create(): SyncComponent = DefaultSyncComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

internal class DefaultSyncComponent(dependencies: SyncComponent.Dependencies) : SyncComponent {

    override val serializer: SyncSerializer = SyncSerializer()

    override val syncEngine: SyncEngine by lazy {
        DefaultSyncEngine(
            categoryPipeline = SyncPipeline(
                source = dependencies.categorySyncSource,
                sink = dependencies.categorySyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            accountPipeline = SyncPipeline(
                source = dependencies.accountSyncSource,
                sink = dependencies.accountSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            transactionPipeline = SyncPipeline(
                source = dependencies.transactionSyncSource,
                sink = dependencies.transactionSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            budgetPipeline = SyncPipeline(
                source = dependencies.budgetSyncSource,
                sink = dependencies.budgetSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            resourceResolver = dependencies.resourceResolver,
            serializer = serializer,
            clock = dependencies.clock,
        )
    }
}
