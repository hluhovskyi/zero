package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel

interface IconPickerViewModel : AttachableActionStateModel<IconPickerViewModel.Action, IconPickerViewModel.State> {

    sealed interface Action {
        data class SelectIcon(val icon: Icon) : Action
    }

    data class State(
        val icons: List<Icon> = emptyList(),
        val selectedIcon: Icon? = null,
        val colorScheme: ColorScheme? = null,
    )
}
