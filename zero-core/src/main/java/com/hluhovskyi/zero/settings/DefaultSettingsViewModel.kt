package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.backup.RelativeAge
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.export.ExportUseCase
import com.hluhovskyi.zero.security.BiometricAuthenticator
import com.hluhovskyi.zero.security.BiometricLockUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val settingsCurrencyUseCase: SettingsCurrencyUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val onBackupSelected: OnBackupSelectedHandler,
    private val onDevChartsSelected: OnDevChartsSelectedHandler,
    private val isDebugBuild: Boolean,
    private val exportUseCase: ExportUseCase,
    private val biometricLockUseCase: BiometricLockUseCase,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val backupUseCase: BackupUseCase,
    private val clock: Clock,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : SettingsViewModel {

    private val mutableState = MutableStateFlow(SettingsViewModel.State(showDeveloperOptions = isDebugBuild))
    override val state: Flow<SettingsViewModel.State> = mutableState

    override fun perform(action: SettingsViewModel.Action) {
        when (action) {
            is SettingsViewModel.Action.Import -> coroutineScope.launch(Dispatchers.Main) {
                onImportSelected.onSelected()
            }
            is SettingsViewModel.Action.OpenBackup -> coroutineScope.launch(Dispatchers.Main) {
                onBackupSelected.onSelected()
            }
            is SettingsViewModel.Action.OpenDevCharts -> coroutineScope.launch(Dispatchers.Main) {
                onDevChartsSelected.onSelected()
            }
            is SettingsViewModel.Action.Export -> coroutineScope.launch {
                when (val result = exportUseCase.export(action.uri)) {
                    ExportUseCase.Result.Success ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Success) }
                    is ExportUseCase.Result.Failure ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Error(result.message)) }
                }
            }
            is SettingsViewModel.Action.OpenCurrencyPicker -> {
                settingsCurrencyUseCase.perform(SettingsCurrencyUseCase.Action.Request)
            }
            is SettingsViewModel.Action.ToggleBiometricLock -> coroutineScope.launch {
                val currentlyEnabled = mutableState.value.biometricLockEnabled
                val reason = if (currentlyEnabled) {
                    BiometricAuthenticator.AuthReason.DisableLock
                } else {
                    BiometricAuthenticator.AuthReason.EnableLock
                }
                when (biometricAuthenticator.authenticate(reason)) {
                    BiometricAuthenticator.Result.Success ->
                        biometricLockUseCase.setEnabled(!currentlyEnabled)
                    BiometricAuthenticator.Result.Failure ->
                        mutableState.update { it.copy(biometricFeedback = SettingsViewModel.BiometricFeedback.AuthFailed) }
                    BiometricAuthenticator.Result.Unavailable ->
                        mutableState.update { it.copy(biometricFeedback = SettingsViewModel.BiometricFeedback.Unavailable) }
                }
            }
            is SettingsViewModel.Action.BiometricFeedbackShown -> {
                mutableState.update { it.copy(biometricFeedback = null) }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            launch {
                mutableState.update { state ->
                    state.copy(selectedCurrencyName = currencyPrimaryUseCase.getPrimaryCurrency().name)
                }
            }
            launch(Dispatchers.Main) {
                settingsCurrencyUseCase.state
                    .filterIsInstance<SettingsCurrencyUseCase.State.Picked>()
                    .collect { picked ->
                        launch(Dispatchers.IO) {
                            currencyPrimaryUseCase.setPrimaryCurrency(picked.currency.id)
                            mutableState.update { state ->
                                state.copy(selectedCurrencyName = picked.currency.name)
                            }
                        }
                    }
            }
            launch {
                biometricLockUseCase.enabled.collect { enabled ->
                    mutableState.update { it.copy(biometricLockEnabled = enabled) }
                }
            }
            launch {
                combine(
                    oauthTokenProvider.isSignedIn,
                    backupUseCase.state,
                ) { isSignedIn, backup ->
                    SettingsViewModel.BackupSummary(
                        isSignedIn = isSignedIn,
                        phase = backup.phase,
                        lastSuccessAge = backup.lastSuccessAt?.let { RelativeAge.of(it, clock) },
                        lastError = backup.lastError,
                        consecutiveFailures = backup.consecutiveFailures,
                    )
                }.collect { summary ->
                    mutableState.update { it.copy(backup = summary) }
                }
            }
        }
    }
}
