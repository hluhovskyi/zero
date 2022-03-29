package com.hluhovskyi.zero.activity.navigation

import androidx.navigation.NavController
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.reflect.KClass

private const val TAG = "NavControllerNavigator"

internal class NavControllerNavigator(
    private val startDestination: Destination,
    private val navController: NavController,
    private val incorrectStateDetector: IncorrectStateDetector,
    logger: Logger,
) : Navigator {

    private val logger = logger.withTag(TAG)

    override fun perform(action: Navigator.Action) {
        when (action) {
            is Navigator.Action.Back -> {
                navController.popBackStack()
            }
            is Navigator.Action.NavigateTo -> {
                navController.navigate(action.destination.routeWith(action.arguments)) {

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
                            val value = bundle.getString(key, null)
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
            if (route != routeWithPlaceholders(destination)) {
                return@mapNotNull null
            }

            val value = backStack.arguments?.getString(argument.key)
            if (value == null) {
                return@mapNotNull null
            }

            when (argumentClass) {
                String::class -> (argument as Argument<String>).withValue(value) as ArgumentValue<T>
                else -> null
            }
        }

    override fun startDestination(): Destination = startDestination

    override fun routeWithPlaceholders(destination: Destination): String {
        var newRoute = destination.route

        val optionalArguments = destination.arguments.filter { it.optional }
        if (optionalArguments.isNotEmpty()) {
            newRoute += '?'
            optionalArguments.forEach { argument ->
                newRoute += "${argument.key}={${argument.key}}"
            }
        }

        return newRoute
    }

    private fun Destination.routeWith(values: List<ArgumentValue<*>>): String {
        values.forEach { value ->
            if (value.argument !in this.arguments) {
                incorrectStateDetector.assert(
                    "Argument with ${value.argument.key} isn't defined in argument list for destination $this"
                )
            }
        }

        var newRoute = route

        val optionalValues = values.filter { it.argument.optional }
        if (optionalValues.isNotEmpty()) {
            newRoute += '?'
            optionalValues.forEach { value ->
                newRoute += "${value.argument.key}=${value.value}"
            }
        }

        return newRoute
    }
}