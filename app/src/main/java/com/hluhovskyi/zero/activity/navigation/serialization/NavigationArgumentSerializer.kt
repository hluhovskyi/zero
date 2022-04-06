package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue

interface NavigationArgumentSerializer {

    fun <T : Any> serialize(argumentValue: ArgumentValue<T>): String

    fun <T : Any> deserialize(argument: Argument<T>, rawValue: String): ArgumentValue<T>
}
