package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.AttachableStateViewModel

interface IconPickerViewModel
    : AttachableStateViewModel<IconPickerViewModel.Action, IconPickerViewModel.State> {

    sealed interface Action {
        data class SelectIcon(val icon: Icon) : Action
    }

    data class State(
        val icons: List<Icon> = emptyList(),
        val selectedIcon: Icon? = null,
    )
}