package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel

interface IconPickerViewModel : AttachableActionStateModel<IconPickerViewModel.Action, IconPickerViewModel.State> {

    sealed interface Action {
        data class SelectIcon(val icon: Icon) : Action
        data class SelectColorScheme(val colorScheme: ColorScheme) : Action
    }

    data class State(
        val sections: List<IconPickerSection> = emptyList(),
        val colorSchemes: List<ColorScheme> = emptyList(),
        val colorSchemeToColor: Map<ColorScheme, Color> = emptyMap(),
        val selectedIcon: Icon? = null,
        val selectedColorScheme: ColorScheme? = null,
    )
}
