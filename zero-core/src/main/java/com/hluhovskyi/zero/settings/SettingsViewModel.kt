package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface SettingsViewModel : AttachableActionStateModel<SettingsViewModel.Action, SettingsViewModel.State> {

    sealed interface Action {
        object Import : Action
    }

    data class State(
        val selectedCurrency: String = "",
    )
}
