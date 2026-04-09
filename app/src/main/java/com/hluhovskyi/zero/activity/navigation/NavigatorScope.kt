package com.hluhovskyi.zero.activity.navigation

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.activity.navigation.route.NavigationRouteResolver
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

internal interface NavigatorScope {

    interface Context {
        val navigator: Navigator
        val arguments: NavigatorEntry.Arguments
    }

    fun composable(
        destination: Destination,
        displayOption: NavigatorEntry.DisplayOption = NavigatorEntry.DisplayOption.FullyVisible,
        view: @Composable Context.() -> Unit,
    ): NavigatorEntry

    fun buildable(
        destination: Destination,
        displayOption: NavigatorEntry.DisplayOption = NavigatorEntry.DisplayOption.FullyVisible,
        view: Context.() -> Buildable<out AttachableViewComponent>,
    ): NavigatorEntry
}

internal class DefaultNavigatorScope(
    private val navigator: Navigator,
    private val navigationRouteResolver: NavigationRouteResolver,
) : NavigatorScope {

    override fun composable(
        destination: Destination,
        displayOption: NavigatorEntry.DisplayOption,
        view: @Composable NavigatorScope.Context.() -> Unit,
    ): NavigatorEntry = ComposeNavigationEntry(
        route = navigationRouteResolver.resolveWithPlaceholders(destination),
        destination = destination,
        displayOption = displayOption,
        view = { arguments -> view(NavigatorContext(navigator, arguments)) },
    )

    override fun buildable(
        destination: Destination,
        displayOption: NavigatorEntry.DisplayOption,
        view: NavigatorScope.Context.() -> Buildable<out AttachableViewComponent>,
    ): NavigatorEntry = ComposeNavigationEntry(
        route = navigationRouteResolver.resolveWithPlaceholders(destination),
        destination = destination,
        displayOption = displayOption,
        view = { arguments -> view(NavigatorContext(navigator, arguments)).AttachWithView() },
    )

    private class NavigatorContext(
        override val navigator: Navigator,
        override val arguments: NavigatorEntry.Arguments,
    ) : NavigatorScope.Context

    private class ComposeNavigationEntry(
        override val route: String,
        override val destination: Destination,
        override val displayOption: NavigatorEntry.DisplayOption,
        override val view: @Composable (arguments: NavigatorEntry.Arguments) -> Unit,
    ) : NavigatorEntry
}
