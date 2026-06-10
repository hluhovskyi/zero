package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.backup.BackupError
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.backup.RelativeAge
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Uri

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        data class Export(val uri: Uri.NonEmpty) : Action
        object OpenCurrencyPicker : Action
        object OpenBackup : Action
        object OpenDevCharts : Action
        object ToggleBiometricLock : Action
        object BiometricFeedbackShown : Action
    }

    sealed interface ExportFeedback {
        object Success : ExportFeedback
        data class Error(val message: String) : ExportFeedback
    }

    sealed interface BiometricFeedback {
        object Unavailable : BiometricFeedback
        object AuthFailed : BiometricFeedback
    }

    data class State(
        val selectedCurrencyName: String = "",
        val exportFeedback: ExportFeedback? = null,
        val biometricLockEnabled: Boolean = false,
        val biometricFeedback: BiometricFeedback? = null,
        val backup: BackupSummary = BackupSummary(),
        val showDeveloperOptions: Boolean = false,
    )

    /**
     * Passthrough of [BackupUseCase.State] plus the [isSignedIn] flag, projected so the row
     * composable can pattern-match without re-querying the use case. [lastSuccessAge] is the one
     * derived field (time math belongs in the ViewModel); the composable only maps fields to the
     * row's secondary text.
     */
    data class BackupSummary(
        val isSignedIn: Boolean = false,
        val phase: BackupUseCase.Phase = BackupUseCase.Phase.Idle,
        val lastSuccessAge: RelativeAge? = null,
        val lastError: BackupError? = null,
        val consecutiveFailures: Int = 0,
    )
}
