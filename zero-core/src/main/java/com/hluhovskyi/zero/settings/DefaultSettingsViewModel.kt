package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.export.ExportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val settingsCurrencyUseCase: SettingsCurrencyUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val exportUseCase: ExportUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : SettingsViewModel {

    private val mutableState = MutableStateFlow(SettingsViewModel.State())
    override val state: Flow<SettingsViewModel.State> = mutableState

    override fun perform(action: SettingsViewModel.Action) {
        when (action) {
            is SettingsViewModel.Action.Import -> coroutineScope.launch(Dispatchers.Main) {
                onImportSelected.onSelected()
            }
            is SettingsViewModel.Action.Export -> coroutineScope.launch {
                when (val result = exportUseCase.export(action.uri)) {
                    ExportUseCase.Result.Success ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Success) }
                    is ExportUseCase.Result.Failure ->
                        mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Error(result.message)) }
                }
            }
            is SettingsViewModel.Action.OpenCurrencyPicker -> {
                settingsCurrencyUseCase.perform(SettingsCurrencyUseCase.Action.Request)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            mutableState.update { state ->
                state.copy(selectedCurrencyName = currencyPrimaryUseCase.getPrimaryCurrency().name)
            }
        }
        coroutineScope.launch(Dispatchers.Main) {
            settingsCurrencyUseCase.state
                .filterIsInstance<SettingsCurrencyUseCase.State.Picked>()
                .collect { picked ->
                    coroutineScope.launch {
                        currencyPrimaryUseCase.setPrimaryCurrency(picked.currency.id)
                        mutableState.update { state ->
                            state.copy(selectedCurrencyName = picked.currency.name)
                        }
                    }
                }
        }
    }
}
