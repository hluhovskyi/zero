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
        data class SelectSource(val source: Source, val requiresFile: Boolean = true) : Action
        data class SelectFile(val uri: Uri.NonEmpty) : Action
        data class SetCategoryStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
        data class SetAccountStrategy(val id: Id.Known, val strategy: ResolveStrategy) : Action
        object ConfirmCategories : Action
        object ConfirmAccounts : Action
        object Confirm : Action
        object Back : Action
        object DismissError : Action
        object Retry : Action
    }

    sealed interface State {
        data class SourceSelection(
            val sources: List<Source>,
            val error: String? = null,
        ) : State
        object FilePicker : State
        object Loading : State
        data class CategoriesReview(
            val categories: List<ImportCategory>,
            val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        ) : State
        data class AccountsReview(
            val accounts: List<ImportAccount>,
            val strategies: Map<Id.Known, ResolveStrategy> = emptyMap(),
        ) : State
        data class TransactionsPreview(
            val transactions: List<ImportTransaction>,
            val totalCount: Int,
            val accounts: List<ImportAccount>,
            val categories: List<ImportCategory>,
        ) : State

        object UpToDate : State

        /** Terminal state of the all-new fast path: the full snapshot was imported without review
         *  because nothing in it overlapped local data. [itemCount] is the total restored entities. */
        data class RestoreSuccess(val itemCount: Int) : State
    }

    object Noop : ImportUseCase {
        override fun perform(action: Action) = Unit
        override val state: Flow<State> = emptyFlow()
        override fun attach(): Closeable = Closeables.empty()
    }
}
