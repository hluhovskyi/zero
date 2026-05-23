package com.hluhovskyi.zero.backup

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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
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

    // --- helpers ---

    private fun TestScope.useCase(client: BackupClient): DefaultBackupUseCase =
        DefaultBackupUseCase(
            syncEngine = FakeSyncEngine(emptySnapshot),
            backupClient = client,
            currentUserRepository = FakeCurrentUserRepository(Id.Known("user-1")),
            coroutineScope = this,
        )

    private fun success(backupId: String = "backup-1"): BackupClient.Result =
        BackupClient.Result.Success(
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
    }

    private class FakeCurrentUserRepository(private val userId: Id.Known) : CurrentUserRepository {
        override fun query() = MutableStateFlow(User(userId))
    }
}
