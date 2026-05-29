package com.hluhovskyi.zero.backup

import androidx.work.NetworkType
import com.hluhovskyi.zero.scheduling.WorkManagerScheduler

internal class DefaultBackupScheduler(
    private val workManagerScheduler: WorkManagerScheduler,
) : BackupScheduler {

    override fun enable(wifiOnly: Boolean) {
        workManagerScheduler.enablePeriodic(
            name = JOB_NAME,
            intervalHours = 24,
            networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            workerClass = DriveBackupSchedulerWorker::class.java,
        )
    }

    override fun disable() {
        workManagerScheduler.cancel(JOB_NAME)
    }

    companion object {
        const val JOB_NAME = "drive-backup-periodic"
    }
}
