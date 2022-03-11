package com.hluhovskyi.zero.common

import kotlinx.coroutines.flow.Flow

interface StateViewModel<Action, State> {

    val state: Flow<State>

    fun perform(action: Action)
}

interface AttachableStateViewModel<Action, State> : StateViewModel<Action, State>, Attachable