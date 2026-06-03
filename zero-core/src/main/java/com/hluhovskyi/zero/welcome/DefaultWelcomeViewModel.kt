package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.backup.OnRestoreSelectedHandler
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.settings.OnImportSelectedHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class DefaultWelcomeViewModel(
    private val onImportSelected: OnImportSelectedHandler,
    private val onRestoreSelected: OnRestoreSelectedHandler,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    WelcomeViewModel {

    private val mutableState = MutableStateFlow(WelcomeViewModel.State())
    override val state: StateFlow<WelcomeViewModel.State> = mutableState

    override fun perform(action: WelcomeViewModel.Action) {
        when (action) {
            is WelcomeViewModel.Action.ImportSelected -> scope.launch(dispatchers.main()) {
                onImportSelected.onSelected()
            }
            is WelcomeViewModel.Action.RestoreSelected -> scope.launch(dispatchers.main()) {
                onRestoreSelected.onSelected()
            }
        }
    }
}
