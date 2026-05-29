package com.hluhovskyi.zero.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hluhovskyi.zero.HasApplicationComponent

internal class DriveBackupSchedulerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = (applicationContext as HasApplicationComponent).applicationComponent
        .workSchedulerComponent
        .performBackup()
}
