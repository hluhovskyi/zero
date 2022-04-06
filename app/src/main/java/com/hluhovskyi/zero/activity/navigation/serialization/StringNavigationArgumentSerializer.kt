package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import kotlin.reflect.KClass

internal object StringNavigationArgumentSerializer : TypedNavigationArgumentSerializer<String>() {
    override val actualClass: KClass<String> = String::class

    override fun performDeserialization(argument: Argument<String>, rawValue: String): ArgumentValue<String> =
        argument.withValue(rawValue)

    override fun performSerialization(argumentValue: ArgumentValue<String>): String = argumentValue.value
}