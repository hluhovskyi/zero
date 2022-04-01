package com.hluhovskyi.zero.imports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultImportViewModel(
    private val importUseCase: ImportUseCase,
) : ImportViewModel {

    override val state: Flow<ImportViewModel.State> =
        importUseCase.state.map { state ->
            val step = when (state) {
                is ImportUseCase.State.FilePicker -> ImportViewModel.Step.FilePicker
                is ImportUseCase.State.AccountsPicker -> ImportViewModel.Step.AccountsPicker
                is ImportUseCase.State.CategoriesPicker -> ImportViewModel.Step.CategoriesPicker
            }

            ImportViewModel.State(step)
        }
}