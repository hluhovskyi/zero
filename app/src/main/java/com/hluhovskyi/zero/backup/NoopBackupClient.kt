package com.hluhovskyi.zero.backup

/**
 * Placeholder [BackupClient] used until Phase 2 lands `DriveBackupClient`. Every call returns
 * [BackupClient.Result.NotFound] / [BackupClient.DownloadResult.NotFound] so the
 * `BackupUseCase` state machine compiles into the app and stays at `Idle` without touching
 * the network.
 */
internal class NoopBackupClient : BackupClient {
    override suspend fun upload(envelope: BackupEnvelope): BackupClient.Result = BackupClient.Result.NotFound
    override suspend fun latest(): BackupClient.Result = BackupClient.Result.NotFound
    override suspend fun download(backupId: String): BackupClient.DownloadResult = BackupClient.DownloadResult.NotFound
    override suspend fun delete(backupId: String): BackupClient.Result = BackupClient.Result.NotFound
}
