package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val BACKUP_FORMAT = 1

internal class DefaultBackupUseCase(
    private val syncEngine: SyncEngine,
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val currentUserRepository: CurrentUserRepository,
    private val coroutineScope: CoroutineScope,
) : BackupUseCase {

    private val mutableState = MutableStateFlow(BackupUseCase.State())

    // Sign-in is owned by the OAuth provider; the use case folds it into its own state so callers
    // read one source of truth. accountLabel only makes sense while signed in.
    override val state: Flow<BackupUseCase.State> = combine(
        oauthTokenProvider.isSignedIn,
        mutableState,
    ) { signedIn, current ->
        current.copy(
            isSignedIn = signedIn,
            accountLabel = if (signedIn) current.accountLabel else null,
        )
    }

    override fun perform(action: BackupUseCase.Action) {
        when (action) {
            is BackupUseCase.Action.Connect -> coroutineScope.launch { performConnect() }
            is BackupUseCase.Action.Disconnect -> coroutineScope.launch { performDisconnect(action.deleteRemote) }
            is BackupUseCase.Action.BackupNow -> {
                if (claimUploading()) coroutineScope.launch { performBackup() }
            }
            is BackupUseCase.Action.RestoreLatest -> coroutineScope.launch { performRestore(action.onSnapshot) }
            is BackupUseCase.Action.SignInFeedbackShown ->
                mutableState.update { it.copy(signInFeedback = null) }
            is BackupUseCase.Action.DisconnectFeedbackShown ->
                mutableState.update { it.copy(disconnectFeedback = null) }
        }
    }

    private suspend fun performConnect() {
        when (val result = oauthTokenProvider.signIn()) {
            is OAuthTokenProvider.Result.Success ->
                mutableState.update { it.copy(accountLabel = result.accountLabel) }
            is OAuthTokenProvider.Result.Failure ->
                mutableState.update { it.copy(signInFeedback = BackupUseCase.SignInFeedback.Failed(result.error)) }
            OAuthTokenProvider.Result.Cancelled ->
                mutableState.update { it.copy(signInFeedback = BackupUseCase.SignInFeedback.Cancelled) }
        }
    }

    private suspend fun performDisconnect(deleteRemote: Boolean) {
        val deleteFailed = deleteRemote && !deleteRemoteBackup()
        // revoke() is local-only (no on-device refresh token); it always runs so the credential is
        // never left dangling, even when the remote delete fails.
        oauthTokenProvider.revoke()
        mutableState.update {
            it.copy(
                disconnectFeedback = if (deleteFailed) {
                    BackupUseCase.DisconnectFeedback.DeleteFailed
                } else {
                    it.disconnectFeedback
                },
            )
        }
    }

    /**
     * Deletes the remote backup file. Returns `false` only when a present file could not be
     * deleted; a missing file (`NotFound`) is treated as success — there is nothing to delete.
     */
    private suspend fun deleteRemoteBackup(): Boolean =
        when (val latest = backupClient.latest()) {
            is BackupClient.Result.Success -> backupClient.delete(latest.metadata.backupId) !is BackupClient.Result.Failure
            BackupClient.Result.NotFound -> true
            is BackupClient.Result.Failure -> false
        }

    private suspend fun performBackup() {
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

    // Coalesces concurrent BackupNow callers at the gate so the second call doesn't even
    // launch a coroutine. update() is itself a CAS retry loop; the captured `claimed` flag is
    // reassigned on each retry — only the iteration whose CAS wins observes the value we return.
    private fun claimUploading(): Boolean {
        var claimed = false
        mutableState.update { state ->
            claimed = state.phase !is BackupUseCase.Phase.Uploading
            if (claimed) state.copy(phase = BackupUseCase.Phase.Uploading) else state
        }
        return claimed
    }
}
