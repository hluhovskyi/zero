package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope

interface BackupComponent {

    interface Dependencies {
        val syncEngine: SyncEngine
        val backupClient: BackupClient
        val currentUserRepository: CurrentUserRepository
        val backupCoroutineScope: CoroutineScope
    }

    val backupUseCase: BackupUseCase

    class Factory(private val dependencies: Dependencies) {
        fun create(): BackupComponent = DefaultBackupComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

internal class DefaultBackupComponent(dependencies: BackupComponent.Dependencies) : BackupComponent {

    override val backupUseCase: BackupUseCase by lazy {
        DefaultBackupUseCase(
            syncEngine = dependencies.syncEngine,
            backupClient = dependencies.backupClient,
            currentUserRepository = dependencies.currentUserRepository,
            coroutineScope = dependencies.backupCoroutineScope,
        )
    }
}
