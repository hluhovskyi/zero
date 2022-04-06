package com.hluhovskyi.zero.common

sealed interface Id {

    object Unknown : Id

    data class Known(val value: String) : Id

    companion object {

        operator fun invoke(value: String): Id.Known = Id.Known(value)

        operator fun invoke(value: String?): Id = value?.let(::invoke) ?: Id.Unknown
    }
}

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