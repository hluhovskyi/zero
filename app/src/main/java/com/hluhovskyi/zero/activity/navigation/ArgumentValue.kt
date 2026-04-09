package com.hluhovskyi.zero.activity.navigation

interface ArgumentValue<Type : Any> {
    val argument: Argument<Type>
    val value: Type
}

internal fun <T : Any> Argument<T>.withValue(value: T): ArgumentValue<T> = GenericArgumentValue(
    argument = this,
    value = value,
)

internal data class GenericArgumentValue<T : Any>(
    override val argument: Argument<T>,
    override val value: T,
) : ArgumentValue<T>
