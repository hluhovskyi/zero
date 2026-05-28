package com.hluhovskyi.zero.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hluhovskyi.zero.HasApplicationComponent
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import timber.log.Timber

internal class DriveBackupSchedulerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val component = (applicationContext as HasApplicationComponent).applicationComponent
        val backupUseCase = component.backupUseCase
        val syncEngine = component.syncEngine
        val currentUserRepository = component.currentUserRepository

        val userId = currentUserRepository.query().first().id

        // No-op skip: compare max(updatedDateTime) with the last successful backup's timestamp.
        val lastModifiedAt = syncEngine.lastModifiedAt(userId)
        val lastSuccessAt = backupUseCase.state.first().lastSuccessAt
        if (lastModifiedAt != null && lastSuccessAt != null && lastModifiedAt <= lastSuccessAt) {
            Timber.d("DriveBackupSchedulerWorker: no-op skip (lastModifiedAt=%s, lastSuccessAt=%s)", lastModifiedAt, lastSuccessAt)
            return Result.success()
        }

        backupUseCase.perform(BackupUseCase.Action.BackupNow)
        val terminal = backupUseCase.state
            .filter { it.phase !is BackupUseCase.Phase.Uploading }
            .first()

        // Return success() even on Failed: a background retry can't fix AuthExpired (no UI for consent)
        // and the failure is already surfaced via the 3-strike notification. WorkManager re-runs the
        // periodic 24h later regardless.
        return when (terminal.phase) {
            is BackupUseCase.Phase.Failed -> Result.success()
            else -> Result.success()
        }
    }
}
