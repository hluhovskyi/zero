package com.hluhovskyi.zero.imports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultImportViewModel(
    private val useCase: ImportUseCase,
) : ImportViewModel {

    override val state: Flow<ImportViewModel.State> = useCase.state.map { useCaseState ->
        when (useCaseState) {
            is ImportUseCase.State.SourceSelection -> ImportViewModel.State.SourceSelection
            is ImportUseCase.State.FilePicker -> ImportViewModel.State.FilePicker
            is ImportUseCase.State.Loading -> ImportViewModel.State.Loading
            is ImportUseCase.State.CategoriesReview -> ImportViewModel.State.CategoriesReview
            is ImportUseCase.State.AccountsReview -> ImportViewModel.State.AccountsReview
            is ImportUseCase.State.TransactionsPreview -> ImportViewModel.State.TransactionsPreview
        }
    }

    override fun perform(action: ImportViewModel.Action) {
        when (action) {
            is ImportViewModel.Action.SelectFile ->
                useCase.perform(ImportUseCase.Action.SelectFile(action.uri))
            is ImportViewModel.Action.Back ->
                useCase.perform(ImportUseCase.Action.Back)
        }
    }
}
