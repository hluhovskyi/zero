package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.user.NewUserUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultHomeViewModel(
    private val newUserUseCase: NewUserUseCase,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    HomeViewModel {

    private val mutableState = MutableStateFlow(HomeViewModel.State())
    override val state: Flow<HomeViewModel.State> = mutableState

    override fun perform(action: HomeViewModel.Action) = Unit

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            newUserUseCase.isNewUser().collect { isNew ->
                mutableState.update { it.copy(isNewUser = isNew) }
            }
        }
    }
}
