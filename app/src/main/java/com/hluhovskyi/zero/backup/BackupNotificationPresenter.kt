package com.hluhovskyi.zero.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.R as AndroidR
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.activity.MainActivity
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.Closeable

internal const val EXTRA_OPEN_SETTINGS_BACKUP = "OPEN_SETTINGS_BACKUP"
private const val CHANNEL_ID = "backup-status"
private const val NOTIFICATION_ID = 9_001
private const val FAILURE_STRIKE_THRESHOLD = 3

internal class BackupNotificationPresenter(
    private val context: Context,
    private val backupUseCase: BackupUseCase,
) : Attachable {

    override fun attach(): Closeable {
        ensureChannel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch {
            backupUseCase.state
                .map { Pair(it.consecutiveFailures, it.lastError) }
                .distinctUntilChanged()
                .collect { (failures, lastError) ->
                    if (failures >= FAILURE_STRIKE_THRESHOLD) {
                        showFailureNotification(lastError)
                    } else {
                        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                    }
                }
        }
        return Closeables.from {
            job.cancel()
            scope.cancel()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.backup_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun showFailureNotification(error: BackupError?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val openSettings = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SETTINGS_BACKUP, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openSettings,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AndroidR.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.backup_notification_title))
            .setContentText(context.getString(bodyStringFor(error)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (security: SecurityException) {
            // POST_NOTIFICATIONS not granted on SDK 33+ — user denied the runtime prompt.
        }
    }

    private fun bodyStringFor(error: BackupError?): Int = when (error) {
        BackupError.AuthExpired -> R.string.backup_notification_body_auth
        BackupError.NetworkUnavailable -> R.string.backup_notification_body_network
        BackupError.QuotaExceeded -> R.string.backup_notification_body_quota
        else -> R.string.backup_notification_body_generic
    }
}
