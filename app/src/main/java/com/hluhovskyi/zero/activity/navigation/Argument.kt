package com.hluhovskyi.zero.activity.navigation

import com.hluhovskyi.zero.common.Id
import kotlin.reflect.KClass

interface Argument<Type : Any> {
    val key: String
    val optional: Boolean
    val fallback: Type?
    val argumentClass: KClass<Type>
}

internal fun stringValueOf(key: String): Argument<String> = StringArgument(key)

internal fun stringOptionalValueOf(key: String): Argument<String> = StringArgument(
    key = key,
    optional = true,
)

private data class StringArgument(
    override val key: String,
    override val optional: Boolean = false,
    override val fallback: String? = null,
    override val argumentClass: KClass<String> = String::class,
) : Argument<String>

internal fun idValueOf(
    key: String,
    fallback: Id = Id.Unknown,
): Argument<Id> = IdArgument(
    key = key,
    fallback = fallback,
)

internal fun idOptionalValueOf(
    key: String,
    fallback: Id = Id.Unknown,
): Argument<Id> = IdArgument(
    key = key,
    fallback = fallback,
    optional = true,
)

private data class IdArgument(
    override val key: String,
    override val optional: Boolean = false,
    override val fallback: Id = Id.Unknown,
    override val argumentClass: KClass<Id> = Id::class,
) : Argument<Id>
