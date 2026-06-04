package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val FAILURE_STRIKE_THRESHOLD = 3

interface BackupComponent {

    interface Dependencies {
        val syncEngine: SyncEngine
        val backupClient: BackupClient
        val oauthTokenProvider: OAuthTokenProvider
        val currentUserRepository: CurrentUserRepository
        val backupCoroutineScope: CoroutineScope
    }

    val backupUseCase: BackupUseCase
    val signal: Flow<BackupSignal>

    class Factory(private val dependencies: Dependencies) {
        fun create(): BackupComponent = DefaultBackupComponent(dependencies)
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}

sealed interface BackupSignal {
    data object Idle : BackupSignal
    data class Failure(val error: BackupError?) : BackupSignal
}

internal class DefaultBackupComponent(dependencies: BackupComponent.Dependencies) : BackupComponent {

    override val backupUseCase: BackupUseCase by lazy {
        DefaultBackupUseCase(
            syncEngine = dependencies.syncEngine,
            backupClient = dependencies.backupClient,
            oauthTokenProvider = dependencies.oauthTokenProvider,
            currentUserRepository = dependencies.currentUserRepository,
            coroutineScope = dependencies.backupCoroutineScope,
        )
    }

    override val signal: Flow<BackupSignal> = backupUseCase.state
        .map { state ->
            if (state.consecutiveFailures >= FAILURE_STRIKE_THRESHOLD) {
                BackupSignal.Failure(state.lastError)
            } else {
                BackupSignal.Idle
            }
        }
        .distinctUntilChanged()
}
