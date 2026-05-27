package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.http.HttpExecutor
import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import com.hluhovskyi.zero.http.HttpExecutor.HttpResponse
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Drive REST implementation of [BackupClient] over the generic [HttpExecutor] +
 * [OAuthTokenProvider] interfaces. Pure Kotlin — no Android or OkHttp imports (enforced by the
 * `KmpReadiness` lint rule).
 *
 * The single backup file is `zero-backup.json` in Drive's hidden `appDataFolder`. v1 [upload]
 * always creates a new file; single-rolling-file replace (Drive PATCH by id) lands with the
 * scheduler in a later phase. [latest] picks the most recently modified file, so restore stays
 * correct even if more than one exists.
 */
internal class DriveBackupClient(
    private val httpExecutor: HttpExecutor,
    private val oauthTokenProvider: OAuthTokenProvider,
    private val envelopeSerializer: BackupEnvelopeSerializer,
    private val baseUrl: String = "https://www.googleapis.com",
) : BackupClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(envelope: BackupEnvelope): BackupClient.Result {
        val token = oauthTokenProvider.getAccessToken() ?: return authExpired()

        val content = envelopeSerializer.serialize(envelope).encodeToByteArray()
        val metadata = """{"name":"$FILE_NAME","parents":["$APP_DATA_FOLDER"]}"""

        val response = httpExecutor.execute(
            HttpRequest(
                method = HttpRequest.Method.POST,
                url = "$baseUrl/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size",
                headers = authHeader(token),
                body = HttpRequest.Body.Multipart(
                    metadataJson = metadata,
                    contentType = "application/json",
                    content = content,
                ),
            ),
        )

        if (response.status != HTTP_OK) return failure(response)
        val file = runCatching { json.decodeFromString<DriveFile>(response.bodyAsString()) }
            .getOrElse { return BackupClient.Result.Failure(BackupError.ParseFailure) }
        return BackupClient.Result.Success(file.toMetadata())
    }

    override suspend fun latest(): BackupClient.Result {
        val token = oauthTokenProvider.getAccessToken() ?: return authExpired()

        val response = httpExecutor.execute(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = "$baseUrl/drive/v3/files?q=name='$FILE_NAME'" +
                    "&spaces=$APP_DATA_FOLDER&fields=files(id,name,modifiedTime,size)",
                headers = authHeader(token),
            ),
        )

        if (response.status != HTTP_OK) return failure(response)
        val list = runCatching { json.decodeFromString<DriveFileList>(response.bodyAsString()) }
            .getOrElse { return BackupClient.Result.Failure(BackupError.ParseFailure) }
        val newest = list.files.maxByOrNull { it.modifiedTime.orEmpty() }
            ?: return BackupClient.Result.NotFound
        return BackupClient.Result.Success(newest.toMetadata())
    }

    override suspend fun download(backupId: String): BackupClient.DownloadResult {
        val token = oauthTokenProvider.getAccessToken()
            ?: return BackupClient.DownloadResult.Failure(BackupError.AuthExpired)

        val response = httpExecutor.execute(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = "$baseUrl/drive/v3/files/$backupId?alt=media",
                headers = authHeader(token),
            ),
        )

        return when {
            response.status == HTTP_OK ->
                runCatching { envelopeSerializer.deserialize(response.bodyAsString()) }
                    .map { BackupClient.DownloadResult.Success(it) }
                    .getOrElse { BackupClient.DownloadResult.Failure(BackupError.ParseFailure) }

            response.status == HTTP_NOT_FOUND -> BackupClient.DownloadResult.NotFound
            else -> BackupClient.DownloadResult.Failure(errorFor(response))
        }
    }

    override suspend fun delete(backupId: String): BackupClient.Result {
        val token = oauthTokenProvider.getAccessToken() ?: return authExpired()

        val response = httpExecutor.execute(
            HttpRequest(
                method = HttpRequest.Method.DELETE,
                url = "$baseUrl/drive/v3/files/$backupId",
                headers = authHeader(token),
            ),
        )

        return when (response.status) {
            HTTP_OK, HTTP_NO_CONTENT -> BackupClient.Result.Success(
                BackupMetadata(
                    backupId = backupId,
                    createdAt = EPOCH,
                    byteSize = 0L,
                    deviceLabel = "",
                ),
            )

            HTTP_NOT_FOUND -> BackupClient.Result.NotFound
            else -> failure(response)
        }
    }

    private fun authHeader(token: String): Map<String, String> = mapOf("Authorization" to "Bearer $token")

    private fun authExpired(): BackupClient.Result = BackupClient.Result.Failure(BackupError.AuthExpired)

    private fun failure(response: HttpResponse): BackupClient.Result = BackupClient.Result.Failure(errorFor(response))

    private fun errorFor(response: HttpResponse): BackupError = when {
        response.status == HTTP_UNAUTHORIZED -> BackupError.AuthExpired
        response.status == HTTP_FORBIDDEN -> BackupError.QuotaExceeded
        response.status in 500..599 -> BackupError.Unknown("Drive server error ${response.status}")
        else -> BackupError.Unknown("Drive error ${response.status}")
    }

    private fun DriveFile.toMetadata(): BackupMetadata = BackupMetadata(
        backupId = id,
        createdAt = modifiedTime?.let { Instant.parse(it).toLocalDateTime(TimeZone.UTC) } ?: EPOCH,
        byteSize = size?.toLongOrNull() ?: 0L,
        deviceLabel = "",
    )

    @Serializable
    private data class DriveFile(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String? = null,
        @SerialName("modifiedTime") val modifiedTime: String? = null,
        @SerialName("size") val size: String? = null,
    )

    @Serializable
    private data class DriveFileList(
        @SerialName("files") val files: List<DriveFile> = emptyList(),
    )

    companion object {
        private const val FILE_NAME = "zero-backup.json"
        private const val APP_DATA_FOLDER = "appDataFolder"

        private const val HTTP_OK = 200
        private const val HTTP_NO_CONTENT = 204
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404

        private val EPOCH = Instant.fromEpochMilliseconds(0).toLocalDateTime(TimeZone.UTC)
    }
}
