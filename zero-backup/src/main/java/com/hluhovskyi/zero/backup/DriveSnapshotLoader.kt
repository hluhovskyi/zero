package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.imports.SnapshotProvider
import com.hluhovskyi.zero.imports.Source
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.flow.first

/**
 * Exposes the latest Google Drive backup as a remote import [Source]. The snapshot is fetched from
 * Drive via [backupClient] (no local file). Format validation lives in the envelope serializer the
 * [backupClient] uses, so [download] already rejects unknown formats.
 */
class DriveSnapshotLoader(
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
) : SnapshotProvider.Remote {

    override val source: Source = DriveSource

    override suspend fun load(): SyncSnapshot {
        ensureSignedIn()

        val metadata = when (val result = backupClient.latest()) {
            is BackupClient.Result.Success -> result.metadata
            is BackupClient.Result.Failure -> error(result.error.toMessage())
            BackupClient.Result.NotFound -> error("No backup found in your Google Drive")
        }

        return when (val result = backupClient.download(metadata.backupId)) {
            is BackupClient.DownloadResult.Success -> result.envelope.snapshot
            is BackupClient.DownloadResult.Failure -> error(result.error.toMessage())
            BackupClient.DownloadResult.NotFound -> error("No backup found in your Google Drive")
        }
    }

    // Ensures a Drive session before fetching. Unlike a one-time-connected source, callers such as
    // the first-launch Welcome restore reach here without having signed in, so trigger the
    // interactive sign-in on demand rather than failing.
    private suspend fun ensureSignedIn() {
        if (oauthTokenProvider.isSignedIn.first()) return
        when (val result = oauthTokenProvider.signIn()) {
            is OAuthTokenProvider.Result.Success -> Unit
            is OAuthTokenProvider.Result.Failure -> error(result.error.toMessage())
            OAuthTokenProvider.Result.Cancelled -> error("Google Drive sign-in was cancelled")
        }
    }

    private fun BackupError.toMessage(): String = when (this) {
        BackupError.AuthExpired -> "Your Google Drive session expired. Sign in again."
        BackupError.NetworkUnavailable -> "No internet connection."
        BackupError.QuotaExceeded -> "Google Drive quota exceeded."
        BackupError.ParseFailure -> "The Drive backup is corrupted or from a newer version of Zero."
        is BackupError.Unknown -> message
    }

    private object DriveSource : Source {
        override val key: String = KEY
    }

    companion object {
        const val KEY = "drive"
    }
}
