package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import kotlin.reflect.KClass

@Suppress("unchecked_cast")
internal abstract class TypedNavigationArgumentSerializer<Type : Any> : NavigationArgumentSerializer {

    abstract val actualClass: KClass<Type>

    override fun <T : Any> deserialize(argument: Argument<T>, rawValue: String): ArgumentValue<T> = if (argument.argumentClass == actualClass) {
        performDeserialization(argument as Argument<Type>, rawValue) as ArgumentValue<T>
    } else {
        assertNotSupported(argument)
    }

    protected abstract fun performDeserialization(argument: Argument<Type>, rawValue: String): ArgumentValue<Type>

    override fun <T : Any> serialize(argumentValue: ArgumentValue<T>): String = if (argumentValue.argument.argumentClass == actualClass) {
        performSerialization(argumentValue as ArgumentValue<Type>)
    } else {
        assertNotSupported(argumentValue.argument)
    }

    protected abstract fun performSerialization(argumentValue: ArgumentValue<Type>): String

    private fun assertNotSupported(argument: Argument<*>): Nothing = throw IllegalStateException("Argument class ${argument.argumentClass} is not supported by this serializer. Supported class: $actualClass")
}
