package com.hluhovskyi.zero.activity.navigation

import androidx.compose.runtime.Composable

internal interface NavigatorEntry {
    val route: String
    val destination: Destination
    val view: @Composable (arguments: Arguments) -> Unit

    interface Arguments {
        operator fun <T : Any> get(key: Argument<T>): ArgumentValue<T>
    }
}

internal fun <T : Any> NavigatorEntry.Arguments.getValue(key: Argument<T>): T = get(key).value