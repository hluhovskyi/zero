package com.hluhovskyi.zero.common

interface Image {

    val uri: Uri
    val description: String

    companion object {

        private val EMPTY = EmptyImage(description = "")

        fun empty(): Image = EMPTY

        operator fun invoke(
            uri: Uri,
            description: String
        ): Image = if (uri is Uri.NonEmpty) {
            UriImage(
                uri = uri,
                description = description
            )
        } else {
            EmptyImage(
                description = description
            )
        }
    }
}

private class EmptyImage(
    override val description: String
) : Image {
    override val uri: Uri = Uri.Empty
}

private class UriImage(
    override val uri: Uri.NonEmpty,
    override val description: String,
): Image