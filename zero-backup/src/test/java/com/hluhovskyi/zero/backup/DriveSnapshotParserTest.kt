package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSnapshotParserTest {

    private val sentinelUri = Uri("drive://latest") as Uri.NonEmpty

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
        val parser = DriveSnapshotParser(FakeBackupClient(), FakeOAuthTokenProvider(token = "tok"))

        assertEquals("drive", parser.source.key)
    }

    @Test
    fun `parse downloads the latest envelope and returns its snapshot`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata("file-7")),
            downloadResult = BackupClient.DownloadResult.Success(BackupEnvelope(format = 1, snapshot = snapshot)),
        )
        val parser = DriveSnapshotParser(client, FakeOAuthTokenProvider(token = "tok"))

        val result = parser.parse(sentinelUri)

        assertEquals(snapshot, result)
        assertEquals(listOf("file-7"), client.downloadedIds)
    }

    @Test
    fun `parse signs in on demand when signed out, then downloads`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata("file-9")),
            downloadResult = BackupClient.DownloadResult.Success(BackupEnvelope(format = 1, snapshot = snapshot)),
        )
        val oauth = FakeOAuthTokenProvider(
            token = null,
            signInResult = OAuthTokenProvider.Result.Success(accountLabel = "user@gmail.com"),
        )
        val parser = DriveSnapshotParser(client, oauth)

        val result = parser.parse(sentinelUri)

        assertEquals(1, oauth.signInCount)
        assertEquals(snapshot, result)
    }

    @Test
    fun `parse throws when signed out and sign-in is cancelled`() = runTest {
        val parser = DriveSnapshotParser(FakeBackupClient(), FakeOAuthTokenProvider(token = null))

        val error = runCatching { parser.parse(sentinelUri) }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }

    @Test
    fun `parse throws when no backup exists`() = runTest {
        val client = FakeBackupClient(latestResult = BackupClient.Result.NotFound)
        val parser = DriveSnapshotParser(client, FakeOAuthTokenProvider(token = "tok"))

        val error = runCatching { parser.parse(sentinelUri) }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }

    @Test
    fun `parse throws when download fails`() = runTest {
        val client = FakeBackupClient(
            latestResult = BackupClient.Result.Success(metadata()),
            downloadResult = BackupClient.DownloadResult.Failure(BackupError.AuthExpired),
        )
        val parser = DriveSnapshotParser(client, FakeOAuthTokenProvider(token = "tok"))

        val error = runCatching { parser.parse(sentinelUri) }.exceptionOrNull()

        assertTrue("Expected IllegalStateException, got $error", error is IllegalStateException)
    }
}
