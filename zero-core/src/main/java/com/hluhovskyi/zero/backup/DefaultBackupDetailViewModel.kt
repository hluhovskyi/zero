package com.hluhovskyi.zero.backup

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
            is BackupDetailViewModel.Action.Connect ->
                backupUseCase.perform(BackupUseCase.Action.Connect)
            is BackupDetailViewModel.Action.BackupNow ->
                backupUseCase.perform(BackupUseCase.Action.BackupNow)
            is BackupDetailViewModel.Action.Restore -> scope.launch(dispatchers.main()) {
                onRestoreSelectedHandler.onSelected()
            }
            is BackupDetailViewModel.Action.Disconnect ->
                mutableState.update { it.copy(confirmDialog = BackupDetailViewModel.ConfirmDialog.Disconnect) }
            is BackupDetailViewModel.Action.DisconnectDismiss ->
                mutableState.update { it.copy(confirmDialog = null) }
            is BackupDetailViewModel.Action.DisconnectConfirmed -> {
                mutableState.update { it.copy(confirmDialog = null) }
                backupUseCase.perform(BackupUseCase.Action.Disconnect(action.deleteRemote))
            }
            is BackupDetailViewModel.Action.Back -> scope.launch(dispatchers.main()) {
                onBackHandler.onBack()
            }
            is BackupDetailViewModel.Action.SignInFeedbackShown ->
                backupUseCase.perform(BackupUseCase.Action.SignInFeedbackShown)
            is BackupDetailViewModel.Action.DisconnectFeedbackShown ->
                backupUseCase.perform(BackupUseCase.Action.DisconnectFeedbackShown)
            is BackupDetailViewModel.Action.SetWifiOnly -> scope.launch(dispatchers.io()) {
                configurationRepository.write(BackupConfigurationKey.WifiOnly, action.wifiOnly)
            }
        }
    }

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            combine(
                backupUseCase.state,
                configurationRepository.observe(BackupConfigurationKey.WifiOnly),
            ) { backup, wifiOnly ->
                backup to wifiOnly
            }.collect { (backup, wifiOnly) ->
                mutableState.update { current ->
                    current.copy(
                        isSignedIn = backup.isSignedIn,
                        accountLabel = backup.accountLabel,
                        phase = backup.phase,
                        lastSuccessAt = backup.lastSuccessAt,
                        lastError = backup.lastError,
                        signInFeedback = backup.signInFeedback,
                        disconnectFeedback = backup.disconnectFeedback,
                        wifiOnly = wifiOnly,
                    )
                }
                if (backup.isSignedIn) {
                    backupScheduler.enable(wifiOnly = wifiOnly)
                } else {
                    backupScheduler.disable()
                }
            }
        }
    }
}
