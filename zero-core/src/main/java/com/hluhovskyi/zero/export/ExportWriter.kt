package com.hluhovskyi.zero.export

fun interface ExportWriter {
    suspend fun write(fileName: String, content: String)
}
