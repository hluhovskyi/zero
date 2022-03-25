package com.hluhovskyi.zero.activity.screens

import com.hluhovskyi.zero.common.ActionStateModel

internal interface Navigator : ActionStateModel<Navigator.Action, Navigator.State> {

    sealed interface Action {
        object Back : Action

        data class NavigateTo(
            val destination: Destination,
            val arguments: List<Destination.ArgumentValue<*>>,
        ) : Action
    }

    data class State(
        val unit: Unit
    )
}

internal fun Navigator.navigateTo(
    destination: Destination,
    vararg arguments: Destination.ArgumentValue<*>,
) {
    perform(
        Navigator.Action.NavigateTo(
            destination = destination,
            arguments = arguments.toList()
        )
    )
}

internal fun Navigator.back() {
    perform(Navigator.Action.Back)
}