package com.hluhovskyi.zero.imports.transactions

import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultImportTransactionPreviewViewModel(
    private val importUseCase: ImportUseCase,
) : ImportTransactionPreviewViewModel {

    override fun perform(action: ImportTransactionPreviewViewModel.Action) {
        when (action) {
            ImportTransactionPreviewViewModel.Action.Submit ->
                importUseCase.perform(ImportUseCase.Action.SubmitTransactions)
        }
    }

    override val state: Flow<ImportTransactionPreviewViewModel.State> =
        importUseCase.state
            .filterIsInstance<ImportUseCase.State.TransactionsPreview>()
            .map { state ->
                ImportTransactionPreviewViewModel.State(
                    items = state.transactions,
                )
            }
}
