package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.backup.BackupError
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Uri
import kotlinx.datetime.LocalDateTime

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        data class Export(val uri: Uri.NonEmpty) : Action
        object OpenCurrencyPicker : Action
        object OpenBackup : Action
        object OpenDevCharts : Action
        object OpenDevCashFlow : Action
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
     * Straight passthrough of [BackupUseCase.State] plus the [isSignedIn] flag, projected so the
     * row composable can pattern-match without re-querying the use case. The composable maps it
     * to the row's secondary text — per `feedback_viewmodel_no_derivation` the ViewModel does no
     * sort/check/mapping here.
     */
    data class BackupSummary(
        val isSignedIn: Boolean = false,
        val phase: BackupUseCase.Phase = BackupUseCase.Phase.Idle,
        val lastSuccessAt: LocalDateTime? = null,
        val lastError: BackupError? = null,
        val consecutiveFailures: Int = 0,
    )
}
