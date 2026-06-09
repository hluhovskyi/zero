package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultBackupConnectionUseCase(
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val coroutineScope: CoroutineScope,
) : BackupConnectionUseCase {

    private val mutableState = MutableStateFlow(BackupConnectionUseCase.State())

    // Sign-in is owned by the OAuth provider; fold it into the state so callers read one source of
    // truth. accountLabel only makes sense while signed in.
    override val state: Flow<BackupConnectionUseCase.State> = combine(
        oauthTokenProvider.isSignedIn,
        mutableState,
    ) { signedIn, current ->
        current.copy(
            isSignedIn = signedIn,
            accountLabel = if (signedIn) current.accountLabel else null,
        )
    }

    override fun perform(action: BackupConnectionUseCase.Action) {
        when (action) {
            is BackupConnectionUseCase.Action.Connect -> coroutineScope.launch { performConnect() }
            is BackupConnectionUseCase.Action.Disconnect -> coroutineScope.launch { performDisconnect(action.deleteRemote) }
            is BackupConnectionUseCase.Action.SignInFeedbackShown ->
                mutableState.update { it.copy(signInFeedback = null) }
            is BackupConnectionUseCase.Action.DisconnectFeedbackShown ->
                mutableState.update { it.copy(disconnectFeedback = null) }
        }
    }

    private suspend fun performConnect() {
        when (val result = oauthTokenProvider.signIn()) {
            is OAuthTokenProvider.Result.Success ->
                mutableState.update { it.copy(accountLabel = result.accountLabel) }
            is OAuthTokenProvider.Result.Failure ->
                mutableState.update { it.copy(signInFeedback = BackupConnectionUseCase.SignInFeedback.Failed(result.error)) }
            OAuthTokenProvider.Result.Cancelled ->
                mutableState.update { it.copy(signInFeedback = BackupConnectionUseCase.SignInFeedback.Cancelled) }
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
                    BackupConnectionUseCase.DisconnectFeedback.DeleteFailed
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
    private suspend fun deleteRemoteBackup(): Boolean = when (val latest = backupClient.latest()) {
        is BackupClient.Result.Success -> backupClient.delete(latest.metadata.backupId) !is BackupClient.Result.Failure
        BackupClient.Result.NotFound -> true
        is BackupClient.Result.Failure -> false
    }
}
