package com.hluhovskyi.zero.activity.screens

internal sealed class Destination(val route: String) {

    open val arguments: List<Argument<*>> = emptyList()

    sealed class Account(route: String) : Destination(route) {
        object All : Account("accounts")
        object Edit : Transaction("accounts/edit")
    }

    sealed class Transaction(route: String) : Destination(route) {
        object All : Transaction("transactions")
        object Edit : Transaction("transactions/edit")
    }

    sealed class Category(route: String) : Destination(route) {
        object All : Category("categories")
        object Edit : Category("categories/edit")
    }

    sealed class Icon(route: String) : Destination(route) {
        object Picker : Icon(route = "icons/picker") {
            override val arguments: List<Argument<*>> = listOf(RequestId)

            object RequestId : Argument<String> by StringArgument(key = "requestId", optional = true)
        }
    }

    interface Argument<Type> {
        val key: String
        val optional: Boolean
    }

    interface ArgumentValue<Type> {
        val argument: Argument<Type>
        val value: Type
    }
}

internal fun Destination.routeWithPlaceholders(arguments: List<Destination.Argument<*>>): String {
    arguments.forEach { argument ->
        if (argument !in this.arguments) {
            throw IllegalStateException("Argument with ${argument.key} isn't defined in argument list for destination $this")
        }
    }

    var newRoute = route

    val optionalArguments = arguments.filter { it.optional }
    if (optionalArguments.isNotEmpty()) {
        newRoute += '?'
        optionalArguments.forEach { argument ->
            newRoute += "${argument.key}={${argument.key}}"
        }
    }

    return newRoute
}

internal fun Destination.routeWith(vararg values: Destination.ArgumentValue<*>): String {
    values.forEach { value ->
        if (value.argument !in this.arguments) {
            throw IllegalStateException("Argument with ${value.argument.key} isn't defined in argument list for destination $this")
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

@Suppress("unchecked_cast")
internal inline fun <reified T> Destination.Argument<T>.withValue(value: T): Destination.ArgumentValue<T> {
    return when (T::class) {
        String::class -> StringArgumentValue(
            argument = this as Destination.Argument<String>,
            value = value as String
        ) as Destination.ArgumentValue<T>

        else -> throw IllegalStateException("Unsupported argument type ${T::class.simpleName}")
    }
}

private data class StringArgument(
    override val key: String,
    override val optional: Boolean = false
) : Destination.Argument<String>

@PublishedApi
internal data class StringArgumentValue(
    override val argument: Destination.Argument<String>,
    override val value: String
) : Destination.ArgumentValue<String>