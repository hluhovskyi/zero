package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSnapshotLoaderTest {

    private val snapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-05-21T10:00:00"),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
        budgets = emptyList(),
    )

    private fun metadata(id: String = "file-1") = BackupMetadata(
        backupId = id,
        createdAt = LocalDateTime.parse("2026-05-21T10:00:00"),
        byteSize = 0L,
        deviceLabel = "",
    )

    @Test
    fun `source advertises the drive key`() {
        val loader = DriveSnapshotLoader(FakeBackupClient(), FakeOAuthTokenProvider(token = "tok"))

        assertEquals("drive", loader.source.key)
    }

    @Test
    fun `load downloads the latest envelope and returns its snapshot`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata("file-7")),
            downloadResult = BackupClient.DownloadResult.Success(BackupEnvelope(format = 1, snapshot = snapshot)),
        )
        val loader = DriveSnapshotLoader(client, FakeOAuthTokenProvider(token = "tok"))

        val result = loader.load()

        assertEquals(snapshot, result)
        assertEquals(listOf("file-7"), client.downloadedIds)
    }

    @Test
    fun `load signs in on demand when signed out, then downloads`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata("file-9")),
            downloadResult = BackupClient.DownloadResult.Success(BackupEnvelope(format = 1, snapshot = snapshot)),
        )
        val oauth = FakeOAuthTokenProvider(
            token = null,
            signInResult = OAuthTokenProvider.Result.Success(accountLabel = "user@gmail.com"),
        )
        val loader = DriveSnapshotLoader(client, oauth)

        val result = loader.load()

        assertEquals(1, oauth.signInCount)
        assertEquals(snapshot, result)
    }

    @Test
    fun `load throws when signed out and sign-in is cancelled`() = runTest {
        val loader = DriveSnapshotLoader(FakeBackupClient(), FakeOAuthTokenProvider(token = null))

        val error = runCatching { loader.load() }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }

    @Test
    fun `load throws when no backup exists`() = runTest {
        val client = FakeBackupClient(latestResult = BackupClient.Result.NotFound)
        val loader = DriveSnapshotLoader(client, FakeOAuthTokenProvider(token = "tok"))

        val error = runCatching { loader.load() }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }

    @Test
    fun `load throws when download fails`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata()),
            downloadResult = BackupClient.DownloadResult.Failure(BackupError.AuthExpired),
        )
        val loader = DriveSnapshotLoader(client, FakeOAuthTokenProvider(token = "tok"))

        val error = runCatching { loader.load() }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }
}
