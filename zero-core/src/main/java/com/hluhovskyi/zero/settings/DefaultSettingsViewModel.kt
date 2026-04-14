package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val settingsCurrencyUseCase: SettingsCurrencyUseCase,
    private val onImportSelected: OnImportSelectedHandler,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
    private val serializer: SyncSerializer,
    private val exportWriter: ExportWriter,
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
                try {
                    val userId = currentUserRepository.query().first().id
                    val snapshot = syncEngine.export(userId)
                    val json = serializer.serialize(snapshot)
                    val date = snapshot.exportedAt.date.toString()
                    exportWriter.write("zero-backup-$date.json", json)
                    mutableState.update { it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Success) }
                } catch (e: Exception) {
                    mutableState.update {
                        it.copy(exportFeedback = SettingsViewModel.ExportFeedback.Error(e.message ?: "Unknown error"))
                    }
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
