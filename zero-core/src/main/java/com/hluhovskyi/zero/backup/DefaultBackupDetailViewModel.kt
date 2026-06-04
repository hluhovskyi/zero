package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.observe
import com.hluhovskyi.zero.config.write
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultBackupDetailViewModel(
    private val backupUseCase: BackupUseCase,
    private val backupScheduler: BackupScheduler,
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val configurationRepository: ConfigurationRepository,
    private val onBackHandler: OnBackHandler,
    private val onRestoreSelectedHandler: OnRestoreSelectedHandler,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    BackupDetailViewModel {

    private val mutableState = MutableStateFlow(BackupDetailViewModel.State())
    override val state: Flow<BackupDetailViewModel.State> = mutableState

    override fun perform(action: BackupDetailViewModel.Action) {
        when (action) {
            is BackupDetailViewModel.Action.Connect -> scope.launch(dispatchers.io()) {
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
            is BackupDetailViewModel.Action.BackupNow -> scope.launch(dispatchers.io()) {
                backupUseCase.perform(BackupUseCase.Action.BackupNow)
            }
            is BackupDetailViewModel.Action.Restore -> scope.launch(dispatchers.main()) {
                onRestoreSelectedHandler.onSelected()
            }
            is BackupDetailViewModel.Action.Disconnect -> {
                mutableState.update { it.copy(confirmDialog = BackupDetailViewModel.ConfirmDialog.Disconnect) }
            }
            is BackupDetailViewModel.Action.DisconnectDismiss -> {
                mutableState.update { it.copy(confirmDialog = null) }
            }
            is BackupDetailViewModel.Action.DisconnectConfirmed -> scope.launch(dispatchers.io()) {
                mutableState.update { it.copy(confirmDialog = null) }
                val deleteFailed = action.deleteRemote && !deleteRemoteBackup()
                oauthTokenProvider.revoke()
                mutableState.update {
                    it.copy(
                        accountLabel = null,
                        disconnectFeedback = if (deleteFailed) {
                            BackupDetailViewModel.DisconnectFeedback.DeleteFailed
                        } else {
                            it.disconnectFeedback
                        },
                    )
                }
            }
            is BackupDetailViewModel.Action.Back -> scope.launch(dispatchers.main()) {
                onBackHandler.onBack()
            }
            is BackupDetailViewModel.Action.SignInFeedbackShown -> {
                mutableState.update { it.copy(signInFeedback = null) }
            }
            is BackupDetailViewModel.Action.DisconnectFeedbackShown -> {
                mutableState.update { it.copy(disconnectFeedback = null) }
            }
            is BackupDetailViewModel.Action.SetWifiOnly -> scope.launch(dispatchers.io()) {
                configurationRepository.write(BackupConfigurationKey.WifiOnly, action.wifiOnly)
            }
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

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            combine(
                oauthTokenProvider.isSignedIn,
                backupUseCase.state,
                configurationRepository.observe(BackupConfigurationKey.WifiOnly),
            ) { isSignedIn, backup, wifiOnly ->
                Triple(isSignedIn, backup, wifiOnly)
            }.collect { (isSignedIn, backup, wifiOnly) ->
                mutableState.update { current ->
                    current.copy(
                        isSignedIn = isSignedIn,
                        phase = backup.phase,
                        lastSuccessAt = backup.lastSuccessAt,
                        lastError = backup.lastError,
                        accountLabel = if (isSignedIn) current.accountLabel else null,
                        wifiOnly = wifiOnly,
                    )
                }
                if (isSignedIn) {
                    backupScheduler.enable(wifiOnly = wifiOnly)
                } else {
                    backupScheduler.disable()
                }
            }
        }
    }
}
