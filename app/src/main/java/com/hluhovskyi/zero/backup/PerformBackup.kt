package com.hluhovskyi.zero.backup

import androidx.work.ListenableWorker
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import timber.log.Timber

class PerformBackup(
    private val backupUseCase: BackupUseCase,
    private val syncEngine: SyncEngine,
    private val currentUserRepository: CurrentUserRepository,
) {

    suspend operator fun invoke(): ListenableWorker.Result {
        val userId = currentUserRepository.query().first().id

        val lastModifiedAt = syncEngine.lastModifiedAt(userId)
        val lastSuccessAt = backupUseCase.state.first().lastSuccessAt
        if (lastModifiedAt != null && lastSuccessAt != null && lastModifiedAt <= lastSuccessAt) {
            Timber.d("PerformBackup: no-op skip (lastModifiedAt=%s, lastSuccessAt=%s)", lastModifiedAt, lastSuccessAt)
            return ListenableWorker.Result.success()
        }

        backupUseCase.perform(BackupUseCase.Action.BackupNow)
        backupUseCase.state
            .filter { it.phase !is BackupUseCase.Phase.Uploading }
            .first()

        return ListenableWorker.Result.success()
    }
}
