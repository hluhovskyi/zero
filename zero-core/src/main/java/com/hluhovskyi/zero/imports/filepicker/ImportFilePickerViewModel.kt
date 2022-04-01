package com.hluhovskyi.zero.imports.filepicker

import com.hluhovskyi.zero.common.ActionStateModel

interface ImportFilePickerViewModel :
    ActionStateModel<ImportFilePickerViewModel.Action, ImportFilePickerViewModel.State> {

    sealed interface Action {
        object Pick : Action
        object Submit : Action
    }

    data class State(
        val fileName: String = ""
    )
}