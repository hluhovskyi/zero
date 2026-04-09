package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.AttachableActionStateModel

interface ColorPickerViewModel : AttachableActionStateModel<ColorPickerViewModel.Action, ColorPickerViewModel.State> {

    sealed interface Action {
        data class SelectColor(val color: Color) : Action
    }

    data class State(
        val colors: List<Color> = emptyList(),
    )
}
