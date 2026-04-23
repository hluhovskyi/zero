package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri

fun interface ExportWriter {
    suspend fun write(uri: Uri.NonEmpty, content: String)
}
