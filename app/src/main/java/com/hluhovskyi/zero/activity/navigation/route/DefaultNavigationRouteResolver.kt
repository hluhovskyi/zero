package com.hluhovskyi.zero.activity.navigation.route

import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.activity.navigation.serialization.NavigationArgumentSerializer
import com.hluhovskyi.zero.common.IncorrectStateDetector

internal class DefaultNavigationRouteResolver(
    private val incorrectStateDetector: IncorrectStateDetector,
    private val navigationArgumentSerializer: NavigationArgumentSerializer,
) : NavigationRouteResolver {

    override fun resolve(
        destination: Destination,
        argumentValues: List<ArgumentValue<*>>
    ): String {
        argumentValues.forEach { value ->
            if (value.argument !in destination.arguments) {
                incorrectStateDetector.assert(
                    "Argument with ${value.argument.key} isn't defined in argument list for destination $this"
                )
            }
        }

        var newRoute = destination.route

        val optionalValues = argumentValues.filter { it.argument.optional }
        if (optionalValues.isNotEmpty()) {
            newRoute += '?'
            newRoute += optionalValues.joinToString("&") { argumentValue ->
                val value = navigationArgumentSerializer.serialize(argumentValue)
                "${argumentValue.argument.key}=$value"
            }
        }

        val requiredValues = argumentValues.filter { !it.argument.optional }
        if (requiredValues.isNotEmpty()) {
            requiredValues.forEach { argumentValue ->
                val value = navigationArgumentSerializer.serialize(argumentValue)
                newRoute = newRoute.replace("{${argumentValue.argument.key}}", value)
            }
        }

        return newRoute
    }

    override fun resolveWithPlaceholders(destination: Destination): String {
        var newRoute = destination.route

        val optionalArguments = destination.arguments.filter { it.optional }
        if (optionalArguments.isNotEmpty()) {
            newRoute += '?'
            newRoute += optionalArguments.joinToString("&") { argument ->
                "${argument.key}={${argument.key}}"
            }
        }

        return newRoute
    }
}