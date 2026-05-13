package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.user.NewUserUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultHomeViewModel(
    private val newUserUseCase: NewUserUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
) : HomeViewModel {

    private val mutableState = MutableStateFlow(HomeViewModel.State())
    override val state: Flow<HomeViewModel.State> = mutableState

    override fun perform(action: HomeViewModel.Action) = Unit

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            newUserUseCase.isNewUser().collect { isNew ->
                mutableState.update { it.copy(isNewUser = isNew) }
            }
        }
    }
}
