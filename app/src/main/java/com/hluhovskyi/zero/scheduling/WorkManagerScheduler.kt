package com.hluhovskyi.zero.scheduling

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkManagerScheduler(private val workManager: WorkManager) {

    fun enablePeriodic(
        name: String,
        intervalHours: Long,
        networkType: NetworkType,
        workerClass: Class<out CoroutineWorker>,
    ) {
        val request = PeriodicWorkRequest.Builder(workerClass, intervalHours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(name: String) {
        workManager.cancelUniqueWork(name)
    }
}
