package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : SettingsViewModel {

    private val mutableState = MutableStateFlow(SettingsViewModel.State())
    override val state: Flow<SettingsViewModel.State> = mutableState

    override fun perform(action: SettingsViewModel.Action) {
        when (action) {
            is SettingsViewModel.Action.Import -> coroutineScope.launch(context = Dispatchers.Main) {
                onImportSelected.onSelected()
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            mutableState.update { state ->
                state.copy(
                    selectedCurrency = currencyPrimaryUseCase.getPrimaryCurrency().name,
                )
            }
        }
    }
}
