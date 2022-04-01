package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.StateModel

interface ImportViewModel : StateModel<ImportViewModel.State> {

    data class State(
        val step: Step = Step.FilePicker,
    )

    enum class Step {
        FilePicker,
        AccountsPicker,
        CategoriesPicker
    }
}