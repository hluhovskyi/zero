package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Uri

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        data class Export(val uri: Uri.NonEmpty) : Action
        object OpenCurrencyPicker : Action
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
    )
}
