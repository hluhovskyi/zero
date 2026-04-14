// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewModel.kt
package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.Source

interface SourceSelectionViewModel : ActionStateModel<SourceSelectionViewModel.Action, SourceSelectionViewModel.State> {

    data class State(val sources: List<Source> = emptyList())

    sealed interface Action {
        data class SelectSource(val source: Source) : Action
        object Close : Action
    }
}
