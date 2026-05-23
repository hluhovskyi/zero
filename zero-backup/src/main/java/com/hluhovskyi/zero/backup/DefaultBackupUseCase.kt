package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val BACKUP_FORMAT = 1

internal class DefaultBackupUseCase(
    private val syncEngine: SyncEngine,
    private val backupClient: BackupClient,
    private val currentUserRepository: CurrentUserRepository,
    private val coroutineScope: CoroutineScope,
) : BackupUseCase {

    private val mutableState = MutableStateFlow(BackupUseCase.State())
    override val state: Flow<BackupUseCase.State> = mutableState.asStateFlow()

    override fun perform(action: BackupUseCase.Action) {
        when (action) {
            is BackupUseCase.Action.BackupNow -> coroutineScope.launch { performBackup() }
            is BackupUseCase.Action.RestoreLatest -> coroutineScope.launch { performRestore(action.onSnapshot) }
        }
    }

    private suspend fun performBackup() {
        if (!tryClaimUploading()) return

        val userId = currentUserRepository.query().first().id
        val snapshot = syncEngine.export(userId)
        val envelope = BackupEnvelope(format = BACKUP_FORMAT, snapshot = snapshot)
        val result = backupClient.upload(envelope)

        mutableState.update { state ->
            when (result) {
                is BackupClient.Result.Success -> state.copy(
                    phase = BackupUseCase.Phase.Idle,
                    lastSuccessAt = result.metadata.createdAt,
                    lastError = null,
                    consecutiveFailures = 0,
                )
                is BackupClient.Result.Failure -> state.copy(
                    phase = BackupUseCase.Phase.Failed(result.error),
                    lastError = result.error,
                    consecutiveFailures = state.consecutiveFailures + 1,
                )
                BackupClient.Result.NotFound -> {
                    val error = BackupError.Unknown("upload returned NotFound")
                    state.copy(
                        phase = BackupUseCase.Phase.Failed(error),
                        lastError = error,
                        consecutiveFailures = state.consecutiveFailures + 1,
                    )
                }
            }
        }
    }

    private suspend fun performRestore(onSnapshot: (SyncSnapshot) -> Unit) {
        mutableState.update { it.copy(phase = BackupUseCase.Phase.Restoring) }

        val backupId = when (val latest = backupClient.latest()) {
            is BackupClient.Result.Success -> latest.metadata.backupId
            is BackupClient.Result.Failure -> {
                markRestoreFailure(latest.error)
                return
            }
            BackupClient.Result.NotFound -> {
                markRestoreFailure(BackupError.ParseFailure)
                return
            }
        }

        when (val download = backupClient.download(backupId)) {
            is BackupClient.DownloadResult.Success -> {
                onSnapshot(download.envelope.snapshot)
                mutableState.update { it.copy(phase = BackupUseCase.Phase.Idle, lastError = null) }
            }
            is BackupClient.DownloadResult.Failure -> markRestoreFailure(download.error)
            BackupClient.DownloadResult.NotFound -> markRestoreFailure(BackupError.ParseFailure)
        }
    }

    private fun markRestoreFailure(error: BackupError) {
        mutableState.update {
            it.copy(
                phase = BackupUseCase.Phase.Failed(error),
                lastError = error,
            )
        }
    }

    // Coalesces concurrent BackupNow callers via CAS. Returns false if another upload is already
    // in flight; otherwise atomically transitions Idle → Uploading.
    private fun tryClaimUploading(): Boolean {
        while (true) {
            val current = mutableState.value
            if (current.phase is BackupUseCase.Phase.Uploading) return false
            if (mutableState.compareAndSet(current, current.copy(phase = BackupUseCase.Phase.Uploading))) return true
        }
    }
}
