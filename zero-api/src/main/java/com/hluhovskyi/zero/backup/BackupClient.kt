package com.hluhovskyi.zero.backup

interface BackupClient {
    suspend fun upload(envelope: BackupEnvelope): Result
    suspend fun latest(): Result
    suspend fun download(backupId: String): DownloadResult
    suspend fun delete(backupId: String): Result

    sealed interface Result {
        data class Success(val metadata: BackupMetadata) : Result
        data class Failure(val error: BackupError) : Result
        object NotFound : Result
    }

    sealed interface DownloadResult {
        data class Success(val envelope: BackupEnvelope) : DownloadResult
        data class Failure(val error: BackupError) : DownloadResult
        object NotFound : DownloadResult
    }
}
