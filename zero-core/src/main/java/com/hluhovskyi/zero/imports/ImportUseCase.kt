package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface ImportUseCase : AttachableActionStateModel<ImportUseCase.Action, ImportUseCase.State> {
    sealed interface Action {
        data class SelectFile(val uri: Uri.NonEmpty) : Action
        data class SelectAccounts(val accountIds: List<Id.Known>) : Action
        data class SelectCategories(val categoryIds: List<Id.Known>) : Action
    }

    sealed interface State {
        object FilePicker : State
        data class AccountsPicker(val accounts: List<ImportAccount>) : State
        data class CategoriesPicker(val categories: List<ImportCategory>) : State
    }

    object Noop : ImportUseCase {
        override fun perform(action: Action) = Unit
        override val state: Flow<State> = emptyFlow()
        override fun attach(): Closeable = Closeables.empty()
    }
}