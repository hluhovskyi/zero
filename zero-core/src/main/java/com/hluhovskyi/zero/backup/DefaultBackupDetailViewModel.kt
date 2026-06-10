package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.Clock
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
    private val backupConnectionUseCase: BackupConnectionUseCase,
    private val backupScheduler: BackupScheduler,
    private val configurationRepository: ConfigurationRepository,
    private val onBackHandler: OnBackHandler,
    private val onRestoreSelectedHandler: OnRestoreSelectedHandler,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : BaseViewModel(dispatchers),
    BackupDetailViewModel {

    private val mutableState = MutableStateFlow(BackupDetailViewModel.State())
    override val state: Flow<BackupDetailViewModel.State> = mutableState

    override fun perform(action: BackupDetailViewModel.Action) {
        when (action) {
            is BackupDetailViewModel.Action.Connect ->
                backupConnectionUseCase.perform(BackupConnectionUseCase.Action.Connect)
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
                backupConnectionUseCase.perform(BackupConnectionUseCase.Action.Disconnect(action.deleteRemote))
            }
            is BackupDetailViewModel.Action.Back -> scope.launch(dispatchers.main()) {
                onBackHandler.onBack()
            }
            is BackupDetailViewModel.Action.SignInFeedbackShown ->
                backupConnectionUseCase.perform(BackupConnectionUseCase.Action.SignInFeedbackShown)
            is BackupDetailViewModel.Action.DisconnectFeedbackShown ->
                backupConnectionUseCase.perform(BackupConnectionUseCase.Action.DisconnectFeedbackShown)
            is BackupDetailViewModel.Action.SetWifiOnly -> scope.launch(dispatchers.io()) {
                configurationRepository.write(BackupConfigurationKey.WifiOnly, action.wifiOnly)
            }
        }
    }

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            combine(
                backupUseCase.state,
                backupConnectionUseCase.state,
                configurationRepository.observe(BackupConfigurationKey.WifiOnly),
            ) { backup, connection, wifiOnly ->
                Triple(backup, connection, wifiOnly)
            }.collect { (backup, connection, wifiOnly) ->
                mutableState.update { current ->
                    current.copy(
                        isSignedIn = connection.isSignedIn,
                        accountLabel = connection.accountLabel,
                        phase = backup.phase,
                        lastSuccessAgo = backup.lastSuccessAt?.let { TimeAgo.of(it, clock) },
                        lastError = backup.lastError,
                        signInFeedback = connection.signInFeedback,
                        disconnectFeedback = connection.disconnectFeedback,
                        wifiOnly = wifiOnly,
                    )
                }
                if (connection.isSignedIn) {
                    backupScheduler.enable(wifiOnly = wifiOnly)
                } else {
                    backupScheduler.disable()
                }
            }
        }
    }
}
