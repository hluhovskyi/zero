package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Uri

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
        data class Export(val uri: Uri.NonEmpty) : Action
        object OpenCurrencyPicker : Action
    }

    sealed interface ExportFeedback {
        object Success : ExportFeedback
        data class Error(val message: String) : ExportFeedback
    }

    data class State(
        val selectedCurrencyName: String = "",
        val exportFeedback: ExportFeedback? = null,
    )
}
