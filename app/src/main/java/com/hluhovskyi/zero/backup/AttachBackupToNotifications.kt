package com.hluhovskyi.zero.backup

import android.content.Context
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.notifications.Importance
import com.hluhovskyi.zero.notifications.Notification
import com.hluhovskyi.zero.notifications.NotificationChannel
import com.hluhovskyi.zero.notifications.Notifier
import com.hluhovskyi.zero.notifications.TapAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.R as AndroidR

private const val CHANNEL_ID = "backup-status"
private const val NOTIFICATION_ID = 9_001
private const val BACKUP_DEEP_LINK_URI = "zero://backup"

@Suppress("FunctionName")
internal fun AttachBackupToNotifications(
    context: Context,
    backupComponent: BackupComponent,
    notifier: Notifier,
): Attachable = Attachable {
    notifier.ensureChannel(
        NotificationChannel(
            id = CHANNEL_ID,
            name = context.getString(R.string.backup_notification_channel),
            importance = Importance.DEFAULT,
        ),
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val job = scope.launch {
        backupComponent.signal.collect { signal ->
            when (signal) {
                is BackupSignal.Failure -> notifier.show(buildFailureNotification(context, signal.error))
                BackupSignal.Idle -> notifier.cancel(NOTIFICATION_ID)
            }
        }
    }
    Closeables.from {
        job.cancel()
        scope.cancel()
    }
}

private fun buildFailureNotification(context: Context, error: BackupError?): Notification = Notification(
    id = NOTIFICATION_ID,
    channelId = CHANNEL_ID,
    title = context.getString(R.string.backup_notification_title),
    body = context.getString(bodyStringFor(error)),
    smallIconResId = AndroidR.drawable.stat_notify_error,
    tapAction = TapAction.DeepLink(BACKUP_DEEP_LINK_URI),
)

private fun bodyStringFor(error: BackupError?): Int = when (error) {
    BackupError.AuthExpired -> R.string.backup_notification_body_auth
    BackupError.NetworkUnavailable -> R.string.backup_notification_body_network
    BackupError.QuotaExceeded -> R.string.backup_notification_body_quota
    else -> R.string.backup_notification_body_generic
}
