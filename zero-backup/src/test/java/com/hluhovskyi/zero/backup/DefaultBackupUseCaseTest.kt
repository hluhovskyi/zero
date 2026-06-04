package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBackupUseCaseTest {

    private val emptySnapshot = SyncSnapshot(
        version = 1,
        userId = Id.Known("user-1"),
        exportedAt = LocalDateTime.parse("2026-05-21T10:00:00"),
        categories = emptyList(),
        accounts = emptyList(),
        transactions = emptyList(),
        budgets = emptyList(),
    )

    @Test
    fun `BackupNow happy path - Idle then Uploading then Idle with timestamp`() = runTest {
        val client = FakeBackupClient(uploadResult = success())
        val useCase = useCase(client = client)

        useCase.perform(BackupUseCase.Action.BackupNow)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertSame(BackupUseCase.Phase.Idle, end.phase)
        assertNotNull(end.lastSuccessAt)
        assertNull(end.lastError)
        assertEquals(0, end.consecutiveFailures)
        assertEquals(1, client.uploadCount)
        assertEquals(emptySnapshot, client.uploadedEnvelopes.single().snapshot)
        assertEquals(1, client.uploadedEnvelopes.single().format)
    }

    @Test
    fun `BackupNow failure - Failed state and failure count increments`() = runTest {
        val client = FakeBackupClient(uploadResult = failure(BackupError.NetworkUnavailable))
        val useCase = useCase(client = client)

        useCase.perform(BackupUseCase.Action.BackupNow)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertEquals(BackupUseCase.Phase.Failed(BackupError.NetworkUnavailable), end.phase)
        assertEquals(BackupError.NetworkUnavailable, end.lastError)
        assertEquals(1, end.consecutiveFailures)
        assertNull(end.lastSuccessAt)
    }

    @Test
    fun `three consecutive failures - counter reaches 3`() = runTest {
        val client = FakeBackupClient(uploadResult = failure(BackupError.NetworkUnavailable))
        val useCase = useCase(client = client)

        repeat(3) {
            useCase.perform(BackupUseCase.Action.BackupNow)
            advanceUntilIdle()
        }

        val end = useCase.state.first()
        assertEquals(3, end.consecutiveFailures)
        assertEquals(3, client.uploadCount)
    }

    @Test
    fun `success after failures resets counter`() = runTest {
        val client = FakeBackupClient(uploadResult = failure(BackupError.NetworkUnavailable))
        val useCase = useCase(client = client)

        repeat(2) {
            useCase.perform(BackupUseCase.Action.BackupNow)
            advanceUntilIdle()
        }
        client.uploadResult = success()
        useCase.perform(BackupUseCase.Action.BackupNow)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertEquals(0, end.consecutiveFailures)
        assertSame(BackupUseCase.Phase.Idle, end.phase)
        assertNotNull(end.lastSuccessAt)
    }

    @Test
    fun `concurrent BackupNow while Uploading is a no-op`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeBackupClient(uploadResult = success()).apply { uploadGate = gate }
        val useCase = useCase(client = client)

        useCase.perform(BackupUseCase.Action.BackupNow)
        // Let the first coroutine reach the gate.
        runCurrent()
        assertSame(BackupUseCase.Phase.Uploading, useCase.state.first().phase)
        // Fire a second BackupNow while the first is in flight.
        useCase.perform(BackupUseCase.Action.BackupNow)
        runCurrent()
        // Second call must have been coalesced — only one upload in flight.
        assertEquals(1, client.uploadCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertSame(BackupUseCase.Phase.Idle, useCase.state.first().phase)
        assertEquals(1, client.uploadCount)
    }

    @Test
    fun `parallel BackupNow from worker and UI - one upload, both observers see Uploading then Idle`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeBackupClient(uploadResult = success()).apply { uploadGate = gate }
        val useCase = useCase(client = client)

        val workerSeenPhases = mutableListOf<BackupUseCase.Phase>()
        val uiSeenPhases = mutableListOf<BackupUseCase.Phase>()
        val workerJob = useCase.state.onEach { workerSeenPhases += it.phase }.launchIn(this)
        val uiJob = useCase.state.onEach { uiSeenPhases += it.phase }.launchIn(this)
        runCurrent()

        useCase.perform(BackupUseCase.Action.BackupNow) // simulate worker trigger
        useCase.perform(BackupUseCase.Action.BackupNow) // simulate UI tap before first completes
        runCurrent()

        assertEquals(1, client.uploadCount)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, client.uploadCount)
        assertTrue("Worker observer saw Uploading", workerSeenPhases.any { it is BackupUseCase.Phase.Uploading })
        assertTrue("UI observer saw Uploading", uiSeenPhases.any { it is BackupUseCase.Phase.Uploading })
        assertSame(BackupUseCase.Phase.Idle, workerSeenPhases.last())
        assertSame(BackupUseCase.Phase.Idle, uiSeenPhases.last())

        workerJob.cancel()
        uiJob.cancel()
    }

    @Test
    fun `RestoreLatest happy path - invokes callback with snapshot`() = runTest {
        val downloadedSnapshot = emptySnapshot.copy(userId = Id.Known("user-2"))
        val client = FakeBackupClient(
            latestResult = success(backupId = "backup-1"),
            downloadResult = BackupClient.DownloadResult.Success(
                BackupEnvelope(format = 1, snapshot = downloadedSnapshot),
            ),
        )
        val useCase = useCase(client = client)
        val received = mutableListOf<SyncSnapshot>()

        useCase.perform(BackupUseCase.Action.RestoreLatest { received += it })
        advanceUntilIdle()

        assertEquals(listOf(downloadedSnapshot), received)
        assertEquals(listOf("backup-1"), client.downloadedIds)
        assertSame(BackupUseCase.Phase.Idle, useCase.state.first().phase)
    }

    @Test
    fun `RestoreLatest with NotFound - error state, callback not invoked`() = runTest {
        val client = FakeBackupClient(latestResult = BackupClient.Result.NotFound)
        val useCase = useCase(client = client)
        val received = mutableListOf<SyncSnapshot>()

        useCase.perform(BackupUseCase.Action.RestoreLatest { received += it })
        advanceUntilIdle()

        assertTrue(received.isEmpty())
        assertTrue(client.downloadedIds.isEmpty())
        val end = useCase.state.first()
        assertEquals(BackupUseCase.Phase.Failed(BackupError.ParseFailure), end.phase)
    }

    @Test
    fun `Connect success - signed in with account label`() = runTest {
        val oauth = FakeOAuthTokenProvider(
            signInResult = OAuthTokenProvider.Result.Success("Google Drive"),
            initiallySignedIn = false,
        )
        val useCase = useCase(client = FakeBackupClient(), oauth = oauth)

        useCase.perform(BackupUseCase.Action.Connect)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertTrue(end.isSignedIn)
        assertEquals("Google Drive", end.accountLabel)
        assertNull(end.signInFeedback)
        assertEquals(1, oauth.signInCount)
    }

    @Test
    fun `Connect cancelled - one-shot Cancelled feedback, still signed out`() = runTest {
        val oauth = FakeOAuthTokenProvider(signInResult = OAuthTokenProvider.Result.Cancelled, initiallySignedIn = false)
        val useCase = useCase(client = FakeBackupClient(), oauth = oauth)

        useCase.perform(BackupUseCase.Action.Connect)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertFalse(end.isSignedIn)
        assertSame(BackupUseCase.SignInFeedback.Cancelled, end.signInFeedback)
    }

    @Test
    fun `Connect failure - one-shot Failed feedback`() = runTest {
        val oauth = FakeOAuthTokenProvider(
            signInResult = OAuthTokenProvider.Result.Failure(BackupError.NetworkUnavailable),
            initiallySignedIn = false,
        )
        val useCase = useCase(client = FakeBackupClient(), oauth = oauth)

        useCase.perform(BackupUseCase.Action.Connect)
        advanceUntilIdle()

        assertEquals(
            BackupUseCase.SignInFeedback.Failed(BackupError.NetworkUnavailable),
            useCase.state.first().signInFeedback,
        )
    }

    @Test
    fun `SignInFeedbackShown clears the one-shot`() = runTest {
        val oauth = FakeOAuthTokenProvider(signInResult = OAuthTokenProvider.Result.Cancelled, initiallySignedIn = false)
        val useCase = useCase(client = FakeBackupClient(), oauth = oauth)

        useCase.perform(BackupUseCase.Action.Connect)
        advanceUntilIdle()
        useCase.perform(BackupUseCase.Action.SignInFeedbackShown)
        advanceUntilIdle()

        assertNull(useCase.state.first().signInFeedback)
    }

    @Test
    fun `Disconnect with delete - deletes remote then revokes, no failure feedback`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(latestResult = success(backupId = "backup-1"), deleteResult = success())
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupUseCase.Action.Disconnect(deleteRemote = true))
        advanceUntilIdle()

        assertEquals(listOf("backup-1"), client.deletedIds)
        assertEquals(1, oauth.revokeCount)
        val end = useCase.state.first()
        assertFalse(end.isSignedIn)
        assertNull(end.disconnectFeedback)
    }

    @Test
    fun `Disconnect with delete failure - still revokes and surfaces DeleteFailed`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(
            latestResult = success(backupId = "backup-1"),
            deleteResult = failure(BackupError.NetworkUnavailable),
        )
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupUseCase.Action.Disconnect(deleteRemote = true))
        advanceUntilIdle()

        assertEquals(listOf("backup-1"), client.deletedIds)
        assertEquals(1, oauth.revokeCount)
        assertSame(BackupUseCase.DisconnectFeedback.DeleteFailed, useCase.state.first().disconnectFeedback)
    }

    @Test
    fun `Disconnect with delete - NotFound is treated as success`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(latestResult = BackupClient.Result.NotFound)
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupUseCase.Action.Disconnect(deleteRemote = true))
        advanceUntilIdle()

        assertTrue(client.deletedIds.isEmpty())
        assertEquals(1, oauth.revokeCount)
        assertNull(useCase.state.first().disconnectFeedback)
    }

    @Test
    fun `Disconnect keeping backup - revokes without touching remote`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(latestResult = success(backupId = "backup-1"), deleteResult = success())
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupUseCase.Action.Disconnect(deleteRemote = false))
        advanceUntilIdle()

        assertEquals(0, client.latestCount)
        assertTrue(client.deletedIds.isEmpty())
        assertEquals(1, oauth.revokeCount)
        assertFalse(useCase.state.first().isSignedIn)
    }

    // --- helpers ---

    private fun TestScope.useCase(
        client: BackupClient,
        oauth: OAuthTokenProvider = FakeOAuthTokenProvider(token = "tok"),
    ): DefaultBackupUseCase = DefaultBackupUseCase(
        syncEngine = FakeSyncEngine(emptySnapshot),
        backupClient = client,
        oauthTokenProvider = oauth,
        currentUserRepository = FakeCurrentUserRepository(Id.Known("user-1")),
        coroutineScope = this,
    )

    private fun success(backupId: String = "backup-1"): BackupClient.Result = BackupClient.Result.Success(
        BackupMetadata(
            backupId = backupId,
            createdAt = LocalDateTime.parse("2026-05-21T11:00:00"),
            byteSize = 0,
            deviceLabel = "test-device",
        ),
    )

    private fun failure(error: BackupError): BackupClient.Result = BackupClient.Result.Failure(error)

    private class FakeSyncEngine(private val snapshot: SyncSnapshot) : SyncEngine {
        override suspend fun export(userId: Id.Known): SyncSnapshot = snapshot.copy(userId = userId)
        override suspend fun import(snapshot: SyncSnapshot, userId: Id.Known) = Unit
        override suspend fun loadSnapshot(uri: Uri.NonEmpty): SyncSnapshot = snapshot
        override suspend fun delta(snapshot: SyncSnapshot, userId: Id.Known): SyncSnapshot = snapshot
        override suspend fun lastModifiedAt(userId: Id.Known): LocalDateTime? = null
    }

    private class FakeCurrentUserRepository(private val userId: Id.Known) : CurrentUserRepository {
        override fun query() = MutableStateFlow(User(userId))
    }
}
