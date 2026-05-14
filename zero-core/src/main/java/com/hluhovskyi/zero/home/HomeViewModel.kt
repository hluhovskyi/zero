package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface HomeViewModel : AttachableActionStateModel<HomeViewModel.Action, HomeViewModel.State> {

    sealed interface Action

    data class State(
        val isNewUser: Boolean = false,
    )

    object Noop : HomeViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
