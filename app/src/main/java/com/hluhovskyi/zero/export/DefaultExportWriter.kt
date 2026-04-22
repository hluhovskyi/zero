package com.hluhovskyi.zero.export

import android.content.Context
import com.hluhovskyi.zero.common.Uri
import android.net.Uri as AndroidUri

internal class DefaultExportWriter(
    private val context: Context,
) : ExportWriter {

    override suspend fun write(uri: Uri.NonEmpty, content: String) {
        val androidUri = AndroidUri.parse(uri.value)
        context.contentResolver.openOutputStream(androidUri)?.use { output ->
            output.write(content.toByteArray())
        } ?: error("Could not open output stream for uri: ${uri.value}")
    }
}
