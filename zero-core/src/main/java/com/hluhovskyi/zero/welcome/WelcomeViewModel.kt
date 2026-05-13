package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface WelcomeViewModel : AttachableActionStateModel<WelcomeViewModel.Action, WelcomeViewModel.State> {

    sealed interface Action {
        object ImportSelected : Action
    }

    class State

    object Noop : WelcomeViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
