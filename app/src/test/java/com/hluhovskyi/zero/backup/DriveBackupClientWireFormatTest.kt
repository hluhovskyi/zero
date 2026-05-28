package com.hluhovskyi.zero.backup

import android.content.Context
import com.hluhovskyi.zero.RemoteComponent
import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.net.URLDecoder

/**
 * Tier 2 wire-format test: real [okhttp3.OkHttpClient] + the real `OkHttpHttpExecutor` (obtained
 * through [RemoteComponent.httpExecutor]) against a [MockWebServer] dressed as Drive. Catches
 * OkHttp-level bugs (multipart boundary, content types, query encoding) and Drive-shape mistakes
 * that the Tier 1 fakes mask.
 */
class DriveBackupClientWireFormatTest {

    private lateinit var server: MockWebServer

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
        """{"id":"file1","name":"zero-backup.json","modifiedTime":"2026-05-21T10:00:00.000Z","size":"42"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun backupClient(token: String? = "test-token"): BackupClient {
        val remote = RemoteComponent.builder(
            object : RemoteComponent.Dependencies {
                override val context: Context = mock()
            },
        ).build()
        return DriveComponent.factory(
            object : DriveComponent.Dependencies {
                override val httpExecutor = remote.httpExecutor
                override val oauthTokenProvider = staticTokenProvider(token)
            },
            baseUrl = server.url("/").toString().removeSuffix("/"),
        ).create().backupClient
    }

    @Test
    fun `upload sends a multipart-related request with auth, metadata, and content`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(driveFileJson))

        backupClient().upload(envelope)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/related"))
        assertTrue(recorded.path!!.contains("uploadType=multipart"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""name":"zero-backup.json""""))
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains(serializer.serialize(envelope)))
    }

    @Test
    fun `upload parses the drive file response into BackupMetadata`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(driveFileJson))

        val result = backupClient().upload(envelope)

        val metadata = (result as BackupClient.Result.Success).metadata
        assertEquals("file1", metadata.backupId)
        assertEquals(42L, metadata.byteSize)
    }

    @Test
    fun `list endpoint sends the appDataFolder query`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[$driveFileJson]}"""))

        backupClient().latest()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        val path = URLDecoder.decode(recorded.path!!, "UTF-8")
        assertTrue(path.contains("q=name='zero-backup.json'"))
        assertTrue(path.contains("spaces=appDataFolder"))
        assertTrue(path.contains("fields=files(id,name,modifiedTime,size)"))
    }

    @Test
    fun `download sends alt=media and parses the body as an envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(serializer.serialize(envelope)))

        val result = backupClient().download("file1")

        assertEquals(BackupClient.DownloadResult.Success(envelope), result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("/drive/v3/files/file1"))
        assertTrue(recorded.path!!.contains("alt=media"))
    }

    @Test
    fun `401 surfaces as AuthExpired`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val result = backupClient().upload(envelope)

        assertEquals(BackupClient.Result.Failure(BackupError.AuthExpired), result)
    }

    @Test
    fun `403 quota surfaces as QuotaExceeded`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"errors":[{"reason":"quotaExceeded"}]}}"""),
        )

        val result = backupClient().upload(envelope)

        assertEquals(BackupClient.Result.Failure(BackupError.QuotaExceeded), result)
    }

    private fun staticTokenProvider(token: String?): OAuthTokenProvider = object : OAuthTokenProvider {
        override suspend fun getAccessToken(): String? = token
        override suspend fun signIn(): OAuthTokenProvider.Result = OAuthTokenProvider.Result.Cancelled
        override suspend fun revoke() = Unit
        override val isSignedIn: Flow<Boolean> = flowOf(token != null)
    }
}
