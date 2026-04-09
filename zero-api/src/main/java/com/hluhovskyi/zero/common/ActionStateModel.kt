package com.hluhovskyi.zero.common

import kotlinx.coroutines.flow.Flow

/** Accepts user actions (button taps, selections, etc.). */
interface ActionModel<Action> {

    fun perform(action: Action)
}

/** Exposes reactive state as a [Flow]. Collected by ViewProviders via `collectAsState()`. */
interface StateModel<State> {

    val state: Flow<State>
}

/** Combines action handling and reactive state — the base contract for UseCases. */
interface ActionStateModel<Action, State> :
    ActionModel<Action>,
    StateModel<State>

/**
 * The standard ViewModel contract: actions in, state out, lifecycle managed.
 * Every feature ViewModel interface extends this.
 */
interface AttachableActionStateModel<Action, State> :
    ActionStateModel<Action, State>,
    Attachable
