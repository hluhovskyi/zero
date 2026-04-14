package com.hluhovskyi.zero.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

internal class DefaultExportWriter(
    private val context: Context,
) : ExportWriter {

    override suspend fun write(fileName: String, content: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            Api29Impl.save(context, fileName, content)
        } else {
            // Fallback for pre-API 29: Use getExternalFilesDir which doesn't require permission
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Could not find external files dir")
            val file = File(dir, fileName)
            file.outputStream().use { output ->
                output.write(content.toByteArray())
            }
        }
    }

    private object Api29Impl {
        fun save(context: Context, fileName: String, content: String) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val insertUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues,
            ) ?: error("Could not create file in Downloads")
            context.contentResolver.openOutputStream(insertUri)?.use { output ->
                output.write(content.toByteArray())
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(insertUri, contentValues, null, null)
        }
    }
}
