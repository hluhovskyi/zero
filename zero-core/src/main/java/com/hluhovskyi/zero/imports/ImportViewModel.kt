package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Uri

interface ImportViewModel : ActionStateModel<ImportViewModel.Action, ImportViewModel.State> {

    sealed interface Action {
        data class SelectFile(val uri: Uri.NonEmpty) : Action
        object Back : Action
    }

    sealed interface State {
        object SourceSelection : State
        object FilePicker : State
        object Loading : State
        object CategoriesReview : State
        object AccountsReview : State
        object TransactionsPreview : State
        object UpToDate : State
    }
}
