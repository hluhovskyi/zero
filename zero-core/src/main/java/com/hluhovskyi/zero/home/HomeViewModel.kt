package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface HomeViewModel : AttachableActionStateModel<HomeViewModel.Action, HomeViewModel.State> {

    override val state: StateFlow<State>

    sealed interface Action

    data class State(
        val isNewUser: Boolean = false,
    )

    object Noop : HomeViewModel {
        override val state: StateFlow<State> = MutableStateFlow(State())
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
