package com.hluhovskyi.zero.imports.transactions

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.imports.ImportTransaction

interface ImportTransactionPreviewViewModel : ActionStateModel<ImportTransactionPreviewViewModel.Action, ImportTransactionPreviewViewModel.State> {

    sealed interface Action {
        object Submit : Action
    }

    data class State(
        val items: List<ImportTransaction> = emptyList(),
    )
}
