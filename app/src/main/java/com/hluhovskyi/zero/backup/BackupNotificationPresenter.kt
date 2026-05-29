package com.hluhovskyi.zero.backup

import android.content.Context
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ScopedAttachable
import com.hluhovskyi.zero.notifications.Importance
import com.hluhovskyi.zero.notifications.Notification
import com.hluhovskyi.zero.notifications.NotificationChannel
import com.hluhovskyi.zero.notifications.Notifier
import com.hluhovskyi.zero.notifications.TapAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import android.R as AndroidR

private const val CHANNEL_ID = "backup-status"
private const val NOTIFICATION_ID = 9_001
private const val FAILURE_STRIKE_THRESHOLD = 3
private const val BACKUP_DEEP_LINK_URI = "zero://backup"

internal class BackupNotificationPresenter(
    private val context: Context,
    private val backupUseCase: BackupUseCase,
    private val notifier: Notifier,
) : ScopedAttachable() {

    override val coroutineContext: CoroutineContext = Dispatchers.IO

    override fun onAttach() {
        notifier.ensureChannel(
            NotificationChannel(
                id = CHANNEL_ID,
                name = context.getString(R.string.backup_notification_channel),
                importance = Importance.DEFAULT,
            ),
        )
        scope.launch {
            backupUseCase.state
                .map { Pair(it.consecutiveFailures, it.lastError) }
                .distinctUntilChanged()
                .collect { (failures, lastError) ->
                    if (failures >= FAILURE_STRIKE_THRESHOLD) {
                        notifier.show(buildFailureNotification(lastError))
                    } else {
                        notifier.cancel(NOTIFICATION_ID)
                    }
                }
        }
    }

    private fun buildFailureNotification(error: BackupError?): Notification = Notification(
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
}
