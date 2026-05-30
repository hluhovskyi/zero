package com.hluhovskyi.zero.imports.sourceselection

import com.hluhovskyi.zero.imports.ImportUseCase
import com.hluhovskyi.zero.imports.OnImportFinishedHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultSourceSelectionViewModel(
    private val importUseCase: ImportUseCase,
    private val onImportFinishedHandler: OnImportFinishedHandler,
) : SourceSelectionViewModel {

    override val state: Flow<SourceSelectionViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.SourceSelection>()
        .map { SourceSelectionViewModel.State(sources = it.sources, error = it.error) }

    override fun perform(action: SourceSelectionViewModel.Action) {
        when (action) {
            is SourceSelectionViewModel.Action.SelectSource ->
                importUseCase.perform(ImportUseCase.Action.SelectSource(action.source, action.requiresFile))
            is SourceSelectionViewModel.Action.Close ->
                onImportFinishedHandler.onFinished()
            is SourceSelectionViewModel.Action.DismissError ->
                importUseCase.perform(ImportUseCase.Action.DismissError)
            is SourceSelectionViewModel.Action.Retry ->
                importUseCase.perform(ImportUseCase.Action.Retry)
        }
    }
}
