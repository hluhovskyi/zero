package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable

internal class DefaultBackupDetailViewModel(
    private val backupUseCase: BackupUseCase,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : BackupDetailViewModel {

    private val mutableState = MutableStateFlow(BackupDetailViewModel.State())
    override val state: Flow<BackupDetailViewModel.State> = mutableState

    override fun perform(action: BackupDetailViewModel.Action) {
        when (action) {
            is BackupDetailViewModel.Action.Connect -> coroutineScope.launch {
                when (val result = oauthTokenProvider.signIn()) {
                    is OAuthTokenProvider.Result.Success ->
                        mutableState.update { it.copy(accountLabel = result.accountLabel) }
                    is OAuthTokenProvider.Result.Failure ->
                        mutableState.update {
                            it.copy(signInFeedback = BackupDetailViewModel.SignInFeedback.Failed(result.error))
                        }
                    OAuthTokenProvider.Result.Cancelled ->
                        mutableState.update {
                            it.copy(signInFeedback = BackupDetailViewModel.SignInFeedback.Cancelled)
                        }
                }
            }
            is BackupDetailViewModel.Action.BackupNow -> coroutineScope.launch {
                backupUseCase.perform(BackupUseCase.Action.BackupNow)
            }
            is BackupDetailViewModel.Action.Restore -> {
                Timber.w("Restore not wired until Phase 5")
            }
            is BackupDetailViewModel.Action.Disconnect -> coroutineScope.launch {
                oauthTokenProvider.revoke()
                mutableState.update { it.copy(accountLabel = null) }
            }
            is BackupDetailViewModel.Action.SignInFeedbackShown -> {
                mutableState.update { it.copy(signInFeedback = null) }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            combine(
                oauthTokenProvider.isSignedIn,
                backupUseCase.state,
            ) { isSignedIn, backup -> isSignedIn to backup }
                .collect { (isSignedIn, backup) ->
                    mutableState.update { current ->
                        current.copy(
                            isSignedIn = isSignedIn,
                            phase = backup.phase,
                            lastSuccessAt = backup.lastSuccessAt,
                            lastError = backup.lastError,
                            accountLabel = if (isSignedIn) current.accountLabel else null,
                        )
                    }
                }
        }
    }
}
