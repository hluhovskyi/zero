package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.Source

interface SourceSelectionViewModel : ActionStateModel<SourceSelectionViewModel.Action, SourceSelectionViewModel.State> {

    data class State(
        val sources: List<Source> = emptyList(),
        val error: String? = null,
    )

    sealed interface Action {
        data class SelectSource(val source: Source, val requiresFile: Boolean = true) : Action
        object Close : Action
        object DismissError : Action
        object Retry : Action
    }
}
