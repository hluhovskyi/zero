package com.hluhovskyi.zero.settings

fun interface ExportWriter {
    suspend fun write(fileName: String, content: String)
}
