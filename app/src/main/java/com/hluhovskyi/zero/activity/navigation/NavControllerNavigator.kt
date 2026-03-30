package com.hluhovskyi.zero.activity.navigation

import androidx.navigation.NavController
import com.hluhovskyi.zero.activity.navigation.route.NavigationRouteResolver
import com.hluhovskyi.zero.activity.navigation.serialization.NavigationArgumentSerializer
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.reflect.KClass

private const val TAG = "NavControllerNavigator"

internal class NavControllerNavigator(
    private val startDestination: Destination,
    private val navController: NavController,
    private val navigationArgumentSerializer: NavigationArgumentSerializer,
    private val navigationRouteResolver: NavigationRouteResolver,
    private val incorrectStateDetector: IncorrectStateDetector,
    logger: Logger,
) : Navigator {

    private val logger = logger.withTag(TAG)

    override fun perform(action: Navigator.Action) {
        logger.d("perform, action=$action")
        when (action) {
            is Navigator.Action.Back -> {
                navController.popBackStack()
            }

            is Navigator.Action.NavigateTo -> {
                val route = navigationRouteResolver.resolve(
                    destination = action.destination,
                    argumentValues = action.arguments
                )
                if (action.clearBackStack && route == navController.graph.startDestinationRoute) {
                    navController.popBackStack(route, false)
                } else {
                    navController.navigate(route) {
                        if (action.clearBackStack) {
                            navController.graph.startDestinationRoute?.let { startRoute ->
                                popUpTo(startRoute)
                            }
                        }
                    }
                }
            }
        }
    }

    override val state: Flow<Navigator.State> =
        navController.currentBackStackEntryFlow
            .map { backStack ->
                Navigator.State(
                    destination = destinationOf(
                        route = backStack.destination.route.orEmpty()
                    ),
                    arguments = backStack.arguments?.let { bundle ->
                        bundle.keySet().mapNotNull { key ->
                            // TODO: handle non string as well
                            val value = bundle.getString(key)
                            if (value != null) {
                                stringValueOf(key).withValue(value)
                            } else {
                                null
                            }
                        }
                    } ?: emptyList()
                )
            }

    @Suppress("unchecked_cast")
    override fun <T : Any> observeArgumentValue(
        destination: Destination,
        argument: Argument<T>,
        argumentClass: KClass<T>,
    ): Flow<ArgumentValue<T>> = navController.currentBackStackEntryFlow
        .mapNotNull { backStack ->
            val route = backStack.destination.route
            if (route != navigationRouteResolver.resolveWithPlaceholders(destination)) {
                return@mapNotNull null
            }

            val rawValue = backStack.arguments?.getString(argument.key)
            if (rawValue == null && argument.optional) {
                return@mapNotNull null
            }

            navigationArgumentSerializer.deserialize(
                argument = argument,
                rawValue = rawValue ?: incorrectStateDetector.assertOrValue(
                    message = "Argument with key ${argument.key} is required to be provided",
                    value = ""
                )
            )
        }

    override fun startDestination(): Destination = startDestination
}