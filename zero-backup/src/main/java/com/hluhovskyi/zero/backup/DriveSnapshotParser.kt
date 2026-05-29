package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.imports.SnapshotParser
import com.hluhovskyi.zero.imports.Source
import com.hluhovskyi.zero.sync.SyncSnapshot
import kotlinx.coroutines.flow.first

/**
 * Exposes the latest Google Drive backup as an import [Source]. The [uri] is ignored — the
 * snapshot is fetched from Drive via [backupClient] rather than read from a local file
 * (hence [DriveSource.requiresFile] is `false`). Format validation lives in the envelope
 * serializer the [backupClient] uses, so [download] already rejects unknown formats.
 */
class DriveSnapshotParser(
    private val backupClient: BackupClient,
    private val oauthTokenProvider: OAuthTokenProvider,
) : SnapshotParser {

    override val source: Source = DriveSource

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot {
        check(oauthTokenProvider.isSignedIn.first()) { "Not signed in to Google Drive" }

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

    private fun BackupError.toMessage(): String = when (this) {
        BackupError.AuthExpired -> "Your Google Drive session expired. Sign in again."
        BackupError.NetworkUnavailable -> "No internet connection."
        BackupError.QuotaExceeded -> "Google Drive quota exceeded."
        BackupError.ParseFailure -> "The Drive backup is corrupted or from a newer version of Zero."
        is BackupError.Unknown -> message
    }

    private object DriveSource : Source {
        override val key: String = KEY
        override val requiresFile: Boolean = false
    }

    companion object {
        const val KEY = "drive"
    }
}
