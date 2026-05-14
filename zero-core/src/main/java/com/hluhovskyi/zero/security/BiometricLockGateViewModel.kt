package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface BiometricLockGateViewModel : AttachableActionStateModel<BiometricLockGateViewModel.Action, BiometricLockGateViewModel.State> {

    sealed interface Action {
        object Unlock : Action
    }

    data class State(
        val isLocked: Boolean = false,
    )

    object Noop : BiometricLockGateViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
