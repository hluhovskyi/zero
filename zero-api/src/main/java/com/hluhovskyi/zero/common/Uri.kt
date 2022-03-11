package com.hluhovskyi.zero.common

sealed interface Uri {

    sealed interface NonEmpty : Uri {
        val value: String
    }

    interface Android : Uri.NonEmpty

    object Empty : Uri

    companion object {

        operator fun invoke(value: String?): Uri = when {
            value == null -> Empty
            value.isEmpty() -> Empty
            value.startsWith("android") -> AnyAndroidUri(value)
            else -> AnyUri(value)
        }
    }
}

private data class AnyUri(override val value: String) : Uri.NonEmpty

private data class AnyAndroidUri(override val value: String) : Uri.NonEmpty