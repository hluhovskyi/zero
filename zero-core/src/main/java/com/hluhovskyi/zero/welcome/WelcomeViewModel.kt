package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface WelcomeViewModel : AttachableActionStateModel<WelcomeViewModel.Action, WelcomeViewModel.State> {

    override val state: StateFlow<State>

    sealed interface Action {
        object ImportSelected : Action
    }

    class State

    object Noop : WelcomeViewModel {
        override val state: StateFlow<State> = MutableStateFlow(State())
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
