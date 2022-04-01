package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultSettingsViewModel(
    private val onImportSelected: OnImportSelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Main)
) : SettingsViewModel {

    override fun perform(action: SettingsViewModel.Action) {
        when (action) {
            is SettingsViewModel.Action.Import -> coroutineScope.launch {
                onImportSelected.onSelected()
            }
        }
    }

    override val state: Flow<SettingsViewModel.State> = emptyFlow()

    override fun attach(): Closeable = Closeables.empty()
}