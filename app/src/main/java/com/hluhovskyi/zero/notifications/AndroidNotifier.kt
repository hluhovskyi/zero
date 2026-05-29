package com.hluhovskyi.zero.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import android.app.NotificationChannel as AndroidNotificationChannel

internal class AndroidNotifier(
    private val context: Context,
) : Notifier {

    override fun ensureChannel(channel: NotificationChannel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channel.id) != null) return
        manager.createNotificationChannel(
            AndroidNotificationChannel(channel.id, channel.name, channel.importance.toAndroid()),
        )
    }

    override fun show(notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, notification.channelId)
            .setSmallIcon(notification.smallIconResId)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(notification.autoCancel)

        notification.tapAction?.let { builder.setContentIntent(it.toPendingIntent()) }

        try {
            manager.notify(notification.id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on SDK 33+ — user denied the runtime prompt.
        }
    }

    override fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun TapAction.toPendingIntent(): PendingIntent = when (this) {
        is TapAction.DeepLink -> {
            val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName)
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun Importance.toAndroid(): Int = when (this) {
        Importance.LOW -> NotificationManager.IMPORTANCE_LOW
        Importance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
        Importance.HIGH -> NotificationManager.IMPORTANCE_HIGH
    }
}
