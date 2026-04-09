package com.hluhovskyi.zero.common

/**
 * Type-safe entity identifier. Use [Unknown] instead of `null` for missing IDs.
 *
 * - `Id("string")` always creates [Known].
 * - `Id(nullableString)` creates [Known] or [Unknown].
 */
sealed interface Id {

    /** Null-equivalent. Use this instead of nullable `Id?`. */
    object Unknown : Id

    data class Known(val value: String) : Id

    companion object {

        operator fun invoke(value: String): Id.Known = Id.Known(value)

        operator fun invoke(value: String?): Id = value?.let(::invoke) ?: Id.Unknown
    }
}

/** Marker interface for entities that have a known [Id]. Used by [associateById] Flow extension. */
interface Identifiable {

    val id: Id.Known
}

fun Id.valueOrNull(): String? = if (this is Id.Known) {
    value
} else {
    null
}

fun Id.valueOrEmpty(): String = if (this is Id.Known) {
    value
} else {
    ""
}

fun List<Identifiable>.joinIdsToString(): String = '[' + joinToString { it.id.value } + ']'
