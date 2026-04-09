package com.hluhovskyi.zero.imports.accounts

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultImportAccountPickerViewModel(
    private val importUseCase: ImportUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : ImportAccountPickerViewModel {

    private val mutableState = MutableStateFlow(ImportAccountPickerViewModel.State())
    override val state: Flow<ImportAccountPickerViewModel.State> = mutableState

    override fun perform(action: ImportAccountPickerViewModel.Action) {
        when (action) {
            is ImportAccountPickerViewModel.Action.ChangeSelection -> {
                mutableState.update { state ->
                    state.copy(
                        items = state.items.map { account ->
                            if (account.id == action.item.id) {
                                account.copy(
                                    selected = !account.selected,
                                )
                            } else {
                                account
                            }
                        },
                    )
                }
            }

            is ImportAccountPickerViewModel.Action.Submit -> {
                importUseCase.perform(
                    ImportUseCase.Action.SelectAccounts(
                        accountIds = mutableState.value.items
                            .filter { it.selected }
                            .map { it.id },
                    ),
                )
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            importUseCase.state
                .map { state ->
                    if (state is ImportUseCase.State.AccountsPicker) {
                        state.accounts.map { account ->
                            ImportAccountPickerViewModel.AccountItem(
                                id = account.id,
                                name = account.name,
                                selected = true,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }
                .collectLatest { accounts ->
                    mutableState.update { state ->
                        state.copy(items = accounts)
                    }
                }
        }
    }
}
