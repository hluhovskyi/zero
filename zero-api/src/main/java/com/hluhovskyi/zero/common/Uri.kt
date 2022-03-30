package com.hluhovskyi.zero.common

sealed interface Uri {

    sealed interface NonEmpty : Uri {
        val value: String
    }

    object Empty : Uri

    companion object {

        operator fun invoke(value: String?): Uri = when {
            value == null -> Empty
            value.isEmpty() -> Empty
            value.startsWith("android") -> AnyAndroidUri(value)
            value.startsWith("file") -> AnyFileUri(value)
            else -> AnyUri(value)
        }
    }
}

val Uri.isFile: Boolean
    get() = this is Uri.NonEmpty && value.startsWith("file")

val Uri.isAndroid: Boolean
    get() = this is Uri.NonEmpty && value.startsWith("android")

private data class AnyUri(override val value: String) : Uri.NonEmpty

private data class AnyAndroidUri(override val value: String) : Uri.NonEmpty

private data class AnyFileUri(override val value: String) : Uri.NonEmpty