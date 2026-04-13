package com.hluhovskyi.zero.sync

interface SyncComponent {

    interface Dependencies {
        val categorySyncSource: EntitySyncSource<SyncCategory>
        val categorySyncSink: EntitySyncSink<SyncCategory>
        val accountSyncSource: EntitySyncSource<SyncAccount>
        val accountSyncSink: EntitySyncSink<SyncAccount>
        val transactionSyncSource: EntitySyncSource<SyncTransaction>
        val transactionSyncSink: EntitySyncSink<SyncTransaction>
    }

    val syncEngine: SyncEngine
}

class DefaultSyncComponent(dependencies: SyncComponent.Dependencies) : SyncComponent {

    override val syncEngine: SyncEngine by lazy {
        DefaultSyncEngine(
            categoryPipeline = SyncPipeline(
                localSource = dependencies.categorySyncSource,
                localSink = dependencies.categorySyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            accountPipeline = SyncPipeline(
                localSource = dependencies.accountSyncSource,
                localSink = dependencies.accountSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
            transactionPipeline = SyncPipeline(
                localSource = dependencies.transactionSyncSource,
                localSink = dependencies.transactionSyncSink,
                resolver = LastWriteWinsResolver(),
            ),
        )
    }
}
