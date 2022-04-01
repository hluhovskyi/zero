package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportUseCase(
    private val importSourceUseCase: ImportSourceUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : ImportUseCase {

    private val mutableState = MutableStateFlow(CompositeState())
    override fun perform(action: ImportUseCase.Action) {
        when (action) {
            is ImportUseCase.Action.SelectFile -> {
                mutableState.update { state ->
                    state.copy(fileToImport = action.uri)
                }
                // TODO: Handle disposal
                coroutineScope.launch {
                    val result = importSourceUseCase.load(ImportSourceUseCase.Request.FromFile(action.uri))
                    mutableState.update { state ->
                        state.copy(
                            accounts = result.accounts,
                            categories = result.categories,
                        )
                    }
                }
            }
            is ImportUseCase.Action.SelectAccounts -> mutableState.update { state ->
                state.copy(selectedAccountIds = action.accountIds)
            }
            is ImportUseCase.Action.SelectCategories -> mutableState.update { state ->
                state.copy(selectedCategoryIds = action.categoryIds)
            }
        }
    }

    override val state: Flow<ImportUseCase.State> = mutableState
        .mapNotNull { state ->
            when {
                state.selectedAccountIds.isNotEmpty() -> ImportUseCase.State.CategoriesPicker(state.categories)
                state.fileToImport is Uri.NonEmpty -> ImportUseCase.State.AccountsPicker(state.accounts)
                else -> ImportUseCase.State.FilePicker
            }
        }

    override fun attach(): Closeable = Closeables.empty()

    private data class CompositeState(
        val fileToImport: Uri = Uri.Empty,
        val accounts: List<ImportAccount> = emptyList(),
        val selectedAccountIds: List<Id.Known> = emptyList(),
        val categories: List<ImportCategory> = emptyList(),
        val selectedCategoryIds: List<Id.Known> = emptyList(),
    )
}