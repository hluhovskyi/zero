package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBackupConnectionUseCaseTest {

    @Test
    fun `Connect success - signed in with account label`() = runTest {
        val oauth = FakeOAuthTokenProvider(
            signInResult = OAuthTokenProvider.Result.Success("Google Drive"),
            initiallySignedIn = false,
        )
        val useCase = useCase(oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Connect)
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
        val useCase = useCase(oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Connect)
        advanceUntilIdle()

        val end = useCase.state.first()
        assertFalse(end.isSignedIn)
        assertSame(BackupConnectionUseCase.SignInFeedback.Cancelled, end.signInFeedback)
    }

    @Test
    fun `Connect failure - one-shot Failed feedback`() = runTest {
        val oauth = FakeOAuthTokenProvider(
            signInResult = OAuthTokenProvider.Result.Failure(BackupError.NetworkUnavailable),
            initiallySignedIn = false,
        )
        val useCase = useCase(oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Connect)
        advanceUntilIdle()

        assertEquals(
            BackupConnectionUseCase.SignInFeedback.Failed(BackupError.NetworkUnavailable),
            useCase.state.first().signInFeedback,
        )
    }

    @Test
    fun `SignInFeedbackShown clears the one-shot`() = runTest {
        val oauth = FakeOAuthTokenProvider(signInResult = OAuthTokenProvider.Result.Cancelled, initiallySignedIn = false)
        val useCase = useCase(oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Connect)
        advanceUntilIdle()
        useCase.perform(BackupConnectionUseCase.Action.SignInFeedbackShown)
        advanceUntilIdle()

        assertNull(useCase.state.first().signInFeedback)
    }

    @Test
    fun `Disconnect with delete - deletes remote then revokes, no failure feedback`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(latestResult = success(backupId = "backup-1"), deleteResult = success())
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Disconnect(deleteRemote = true))
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

        useCase.perform(BackupConnectionUseCase.Action.Disconnect(deleteRemote = true))
        advanceUntilIdle()

        assertEquals(listOf("backup-1"), client.deletedIds)
        assertEquals(1, oauth.revokeCount)
        assertSame(BackupConnectionUseCase.DisconnectFeedback.DeleteFailed, useCase.state.first().disconnectFeedback)
    }

    @Test
    fun `Disconnect with delete - NotFound is treated as success`() = runTest {
        val oauth = FakeOAuthTokenProvider(token = "tok")
        val client = FakeBackupClient(latestResult = BackupClient.Result.NotFound)
        val useCase = useCase(client = client, oauth = oauth)

        useCase.perform(BackupConnectionUseCase.Action.Disconnect(deleteRemote = true))
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

        useCase.perform(BackupConnectionUseCase.Action.Disconnect(deleteRemote = false))
        advanceUntilIdle()

        assertEquals(0, client.latestCount)
        assertTrue(client.deletedIds.isEmpty())
        assertEquals(1, oauth.revokeCount)
        assertFalse(useCase.state.first().isSignedIn)
    }

    // --- helpers ---

    private fun TestScope.useCase(
        client: BackupClient = FakeBackupClient(),
        oauth: OAuthTokenProvider,
    ): DefaultBackupConnectionUseCase = DefaultBackupConnectionUseCase(
        backupClient = client,
        oauthTokenProvider = oauth,
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
}
