package com.hluhovskyi.zero.common

import kotlinx.coroutines.flow.Flow

interface ActionModel<Action> {

    fun perform(action: Action)
}

interface StateModel<State> {

    val state: Flow<State>
}

interface ActionStateModel<Action, State> : ActionModel<Action>, StateModel<State>

interface AttachableActionStateModel<Action, State> : ActionStateModel<Action, State>, Attachable