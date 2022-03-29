package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

internal interface NavigatorEntry {
    val destination: Destination
    val view: @Composable () -> Unit
}

internal fun navigationEntryOf(
    destination: Destination,
    view: @Composable () -> Unit
): NavigatorEntry = ComposeNavigationEntry(
    destination = destination,
    view = view,
)

internal fun navigationEntryOf(
    destination: Destination,
    attachableViewComponentBuilder: Buildable<out AttachableViewComponent>,
): NavigatorEntry = navigationEntryOf(
    destination = destination,
    view = { attachableViewComponentBuilder.AttachWithView() }
)

private class ComposeNavigationEntry(
    override val destination: Destination,
    override val view: @Composable () -> Unit
) : NavigatorEntry