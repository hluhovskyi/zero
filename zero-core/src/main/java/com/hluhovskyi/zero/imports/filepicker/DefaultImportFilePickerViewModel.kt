package com.hluhovskyi.zero.imports.filepicker

import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class DefaultImportFilePickerViewModel(
    private val importUseCase: ImportUseCase,
) : ImportFilePickerViewModel {

    private val mutableState = MutableStateFlow(ImportFilePickerViewModel.State())
    override val state: Flow<ImportFilePickerViewModel.State> = mutableState

    override fun perform(action: ImportFilePickerViewModel.Action) {
        when (action) {
            ImportFilePickerViewModel.Action.Pick -> {
                mutableState.update { state ->
                    state.copy(
                        fileName = "zenmoney.csv",
                    )
                }
            }
            ImportFilePickerViewModel.Action.Submit -> {
                importUseCase.perform(
                    ImportUseCase.Action.SelectFile(
                        Uri("android.resource://com.hluhovskyi.zero/raw/zenmoney") as Uri.NonEmpty,
                    ),
                )
            }
        }
    }
}
