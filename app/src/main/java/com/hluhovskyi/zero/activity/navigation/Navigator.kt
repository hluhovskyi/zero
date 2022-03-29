package com.hluhovskyi.zero.activity.navigation

import com.hluhovskyi.zero.common.ActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal interface Navigator : ActionStateModel<Navigator.Action, Navigator.State> {

    sealed interface Action {
        object Back : Action

        data class NavigateTo(
            val destination: Destination,
            val arguments: List<ArgumentValue<*>>,
        ) : Action
    }

    data class State(
        val destination: Destination,
        val arguments: List<ArgumentValue<*>>,
    )

    fun <T : Any> observeArgumentValue(
        destination: Destination,
        argument: Argument<T>,
        argumentClass: KClass<T>
    ): Flow<ArgumentValue<T>>

    fun startDestination(): Destination

    fun routeWithPlaceholders(destination: Destination): String
}

internal inline fun <reified T : Any> Navigator.observeArgumentValue(
    destination: Destination,
    argument: Argument<T>,
): Flow<ArgumentValue<T>> = observeArgumentValue(
    destination = destination,
    argument = argument,
    argumentClass = T::class
)

internal fun Navigator.navigateTo(
    destination: Destination,
    vararg arguments: ArgumentValue<*>,
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