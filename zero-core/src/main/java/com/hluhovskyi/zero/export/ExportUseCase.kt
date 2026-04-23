package com.hluhovskyi.zero.export

import com.hluhovskyi.zero.common.Uri

interface ExportUseCase {
    suspend fun export(uri: Uri.NonEmpty): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val message: String) : Result
    }
}
