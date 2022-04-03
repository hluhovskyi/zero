package com.hluhovskyi.zero.activity.navigation

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import kotlin.reflect.KClass

internal interface NavigatorEntry {
    val destination: Destination
    val view: @Composable (arguments: Arguments) -> Unit

    interface Arguments {
        operator fun <T : Any> get(key: Argument<T>, argumentClass: KClass<T>): ArgumentValue<T>
    }
}

internal inline operator fun <reified T : Any> NavigatorEntry.Arguments.get(
    key: Argument<T>,
): ArgumentValue<T> = get(key, T::class)

internal fun navigationEntryOf(
    destination: Destination,
    viewProvider: (arguments: NavigatorEntry.Arguments) -> Buildable<out AttachableViewComponent>,
): NavigatorEntry = ComposeNavigationEntry(
    destination = destination,
    view = { arguments -> viewProvider(arguments).AttachWithView() },
)

internal fun composableNavigationEntryOf(
    destination: Destination,
    viewProvider: @Composable (arguments: NavigatorEntry.Arguments) -> Unit,
): NavigatorEntry = ComposeNavigationEntry(
    destination = destination,
    view = viewProvider,
)

private class ComposeNavigationEntry(
    override val destination: Destination,
    override val view: @Composable (arguments: NavigatorEntry.Arguments) -> Unit
) : NavigatorEntry