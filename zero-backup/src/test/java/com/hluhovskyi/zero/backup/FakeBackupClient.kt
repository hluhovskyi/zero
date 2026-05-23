package com.hluhovskyi.zero.backup

import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory test double for [BackupClient]. Each method's result is configurable; calls are
 * recorded for assertion. A [uploadGate] allows tests to control when an in-flight upload
 * completes, enabling deterministic concurrency tests without `delay` or polling.
 */
class FakeBackupClient(
    var uploadResult: BackupClient.Result = BackupClient.Result.NotFound,
    var latestResult: BackupClient.Result = BackupClient.Result.NotFound,
    var downloadResult: BackupClient.DownloadResult = BackupClient.DownloadResult.NotFound,
    var deleteResult: BackupClient.Result = BackupClient.Result.NotFound,
) : BackupClient {

    val uploadedEnvelopes = mutableListOf<BackupEnvelope>()
    val downloadedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
    var uploadCount = 0
        private set
    var latestCount = 0
        private set

    /** When set, [upload] suspends on this deferred before returning [uploadResult]. */
    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun upload(envelope: BackupEnvelope): BackupClient.Result {
        uploadCount++
        uploadedEnvelopes += envelope
        uploadGate?.await()
        return uploadResult
    }

    override suspend fun latest(): BackupClient.Result {
        latestCount++
        return latestResult
    }

    override suspend fun download(backupId: String): BackupClient.DownloadResult {
        downloadedIds += backupId
        return downloadResult
    }

    override suspend fun delete(backupId: String): BackupClient.Result {
        deletedIds += backupId
        return deleteResult
    }
}
