package com.hluhovskyi.zero.activity.navigation

interface ArgumentValue<Type : Any> {
    val argument: Argument<Type>
    val value: Type
}

@Suppress("unchecked_cast")
internal inline fun <reified T : Any> Argument<T>.withValue(value: T): ArgumentValue<T> {
    return when (T::class) {
        String::class -> StringArgumentValue(
            argument = this as Argument<String>,
            value = value as String
        ) as ArgumentValue<T>

        else -> throw IllegalStateException("Unsupported argument type ${T::class.simpleName}")
    }
}

@PublishedApi
internal data class StringArgumentValue(
    override val argument: Argument<String>,
    override val value: String
) : ArgumentValue<String>
