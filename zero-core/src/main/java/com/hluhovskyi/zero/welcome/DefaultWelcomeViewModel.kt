package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.settings.OnImportSelectedHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultWelcomeViewModel(
    private val onImportSelected: OnImportSelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : WelcomeViewModel {

    private val mutableState = MutableStateFlow(WelcomeViewModel.State())
    override val state: Flow<WelcomeViewModel.State> = mutableState

    override fun perform(action: WelcomeViewModel.Action) {
        when (action) {
            is WelcomeViewModel.Action.ImportSelected -> coroutineScope.launch(Dispatchers.Main) {
                onImportSelected.onSelected()
            }
        }
    }

    override fun attach(): Closeable = Closeables.empty()
}
