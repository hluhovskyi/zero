// zero-core/src/main/java/com/hluhovskyi/zero/settings/ExportWriter.kt
package com.hluhovskyi.zero.settings

fun interface ExportWriter {
    suspend fun write(fileName: String, content: String)
}
