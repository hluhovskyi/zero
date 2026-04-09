package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue

internal class CompositeNavigationArgumentSerializer(
    serializers: Set<TypedNavigationArgumentSerializer<*>> = setOf(
        StringNavigationArgumentSerializer,
        IdNavigationArgumentSerializer,
    ),
) : NavigationArgumentSerializer {

    private val classToSerializer = serializers.associateBy { it.actualClass }

    override fun <T : Any> serialize(argumentValue: ArgumentValue<T>): String {
        val argument = argumentValue.argument
        val serializer = classToSerializer[argument.argumentClass] ?: assertNoSerializer(argument)
        return serializer.serialize(argumentValue)
    }

    override fun <T : Any> deserialize(argument: Argument<T>, rawValue: String): ArgumentValue<T> {
        val serializer = classToSerializer[argument.argumentClass] ?: assertNoSerializer(argument)
        return serializer.deserialize(argument, rawValue)
    }

    private fun assertNoSerializer(argument: Argument<*>): Nothing = throw IllegalStateException(
        "No serializer found for class ${argument.argumentClass}." +
            " Available serializers: ${classToSerializer.keys}",
    )
}
