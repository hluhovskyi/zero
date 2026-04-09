package com.hluhovskyi.zero.activity.navigation

internal interface Destination {
    val route: String
    val arguments: List<Argument<*>>
}

internal fun destinationOf(
    route: String,
    vararg arguments: Argument<*>,
): Destination = DestinationValue(
    route = route,
    arguments = arguments.toList(),
)

private data class DestinationValue(
    override val route: String,
    override val arguments: List<Argument<*>>,
) : Destination
