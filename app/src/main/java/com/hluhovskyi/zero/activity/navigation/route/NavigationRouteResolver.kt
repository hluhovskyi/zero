package com.hluhovskyi.zero.activity.navigation.route

import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.Destination

internal interface NavigationRouteResolver {

    fun resolve(
        destination: Destination,
        argumentValues: List<ArgumentValue<*>>,
    ): String

    fun resolveWithPlaceholders(
        destination: Destination,
    ): String
}
