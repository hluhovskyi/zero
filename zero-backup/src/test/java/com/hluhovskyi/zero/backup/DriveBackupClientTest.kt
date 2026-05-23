package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.http.HttpExecutor.HttpRequest
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupClientTest {

    private val serializer = BackupEnvelopeSerializer()

    private val envelope = BackupEnvelope(
        format = 1,
        snapshot = SyncSnapshot(
            version = 1,
            userId = Id.Known("user-1"),
            exportedAt = LocalDateTime.parse("2026-05-21T10:00:00"),
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
        ),
    )

    private val driveFileJson =
        """{"id":"file1","name":"zero-backup.json","modifiedTime":"2026-05-21T10:00:00.000Z","size":"123"}"""

    private fun client(http: FakeHttpExecutor, token: String? = "tok") = DriveBackupClient(
        httpExecutor = http,
        oauthTokenProvider = FakeOAuthTokenProvider(token),
        envelopeSerializer = serializer,
    )

    @Test
    fun `upload posts multipart and parses metadata`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(200, driveFileJson) }

        val result = client(http).upload(envelope)

        assertEquals(BackupClient.Result.Success::class, result::class)
        val metadata = (result as BackupClient.Result.Success).metadata
        assertEquals("file1", metadata.backupId)
        assertEquals(123L, metadata.byteSize)

        val request = http.lastRequest
        assertEquals(HttpRequest.Method.POST, request.method)
        assertTrue(request.url.contains("/upload/drive/v3/files"))
        assertTrue(request.url.contains("uploadType=multipart"))
        assertEquals("Bearer tok", request.headers["Authorization"])
        val body = request.body as HttpRequest.Body.Multipart
        assertTrue(body.metadataJson.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.metadataJson.contains(""""name":"zero-backup.json""""))
        assertEquals(serializer.serialize(envelope), body.content.decodeToString())
    }

    @Test
    fun `upload without token fails as AuthExpired and makes no request`() = runTest {
        val http = FakeHttpExecutor()

        val result = client(http, token = null).upload(envelope)

        assertEquals(BackupClient.Result.Failure(BackupError.AuthExpired), result)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `upload 401 fails as AuthExpired`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(401, "unauthorized") }

        val result = client(http).upload(envelope)

        assertEquals(BackupClient.Result.Failure(BackupError.AuthExpired), result)
    }

    @Test
    fun `upload 403 fails as QuotaExceeded`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(403, "quota") }

        val result = client(http).upload(envelope)

        assertEquals(BackupClient.Result.Failure(BackupError.QuotaExceeded), result)
    }

    @Test
    fun `upload 5xx fails as Unknown`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(503, "unavailable") }

        val result = client(http).upload(envelope)

        assertTrue(result is BackupClient.Result.Failure)
        assertTrue((result as BackupClient.Result.Failure).error is BackupError.Unknown)
    }

    @Test
    fun `latest returns metadata for the listed file`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(200, """{"files":[$driveFileJson]}""") }

        val result = client(http).latest()

        val metadata = (result as BackupClient.Result.Success).metadata
        assertEquals("file1", metadata.backupId)

        val request = http.lastRequest
        assertEquals(HttpRequest.Method.GET, request.method)
        assertTrue(request.url.contains("spaces=appDataFolder"))
        assertTrue(request.url.contains("q=name='zero-backup.json'"))
    }

    @Test
    fun `latest returns NotFound when no files`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(200, """{"files":[]}""") }

        val result = client(http).latest()

        assertEquals(BackupClient.Result.NotFound, result)
    }

    @Test
    fun `download fetches alt=media and deserializes envelope`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(200, serializer.serialize(envelope)) }

        val result = client(http).download("file1")

        assertEquals(BackupClient.DownloadResult.Success(envelope), result)
        val request = http.lastRequest
        assertEquals(HttpRequest.Method.GET, request.method)
        assertTrue(request.url.contains("/drive/v3/files/file1?alt=media"))
    }

    @Test
    fun `download of unknown format fails as ParseFailure`() = runTest {
        val futureJson = serializer.serialize(envelope).replaceFirst("\"format\":1", "\"format\":99")
        val http = FakeHttpExecutor().apply { enqueue(200, futureJson) }

        val result = client(http).download("file1")

        assertEquals(BackupClient.DownloadResult.Failure(BackupError.ParseFailure), result)
    }

    @Test
    fun `delete issues DELETE and returns Success`() = runTest {
        val http = FakeHttpExecutor().apply { enqueue(204) }

        val result = client(http).delete("file1")

        assertEquals("file1", (result as BackupClient.Result.Success).metadata.backupId)
        val request = http.lastRequest
        assertEquals(HttpRequest.Method.DELETE, request.method)
        assertTrue(request.url.contains("/drive/v3/files/file1"))
    }
}
