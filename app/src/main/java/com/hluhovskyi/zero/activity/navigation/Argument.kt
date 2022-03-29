package com.hluhovskyi.zero.activity.navigation

interface Argument<Type> {
    val key: String
    val optional: Boolean
}

internal fun stringValueOf(key: String): Argument<String> = StringArgument(key)

internal fun stringOptionalValueOf(key: String): Argument<String> = StringArgument(
    key = key,
    optional = true
)

private data class StringArgument(
    override val key: String,
    override val optional: Boolean = false
) : Argument<String>