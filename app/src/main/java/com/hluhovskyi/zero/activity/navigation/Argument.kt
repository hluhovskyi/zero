package com.hluhovskyi.zero.activity.navigation

import kotlin.reflect.KClass

interface Argument<Type : Any> {
    val key: String
    val optional: Boolean
    val argumentClass: KClass<Type>
}

internal fun stringValueOf(key: String): Argument<String> = StringArgument(key)

internal fun stringOptionalValueOf(key: String): Argument<String> = StringArgument(
    key = key,
    optional = true
)

private data class StringArgument(
    override val key: String,
    override val optional: Boolean = false,
    override val argumentClass: KClass<String> = String::class,
) : Argument<String>