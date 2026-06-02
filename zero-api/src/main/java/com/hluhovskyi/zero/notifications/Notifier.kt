package com.hluhovskyi.zero.notifications

interface Notifier {

    fun ensureChannel(channel: NotificationChannel)

    fun show(notification: Notification)

    fun cancel(id: Int)

    object Noop : Notifier {
        override fun ensureChannel(channel: NotificationChannel) = Unit
        override fun show(notification: Notification) = Unit
        override fun cancel(id: Int) = Unit
    }
}

data class NotificationChannel(
    val id: String,
    val name: String,
    val importance: Importance,
)

enum class Importance { LOW, DEFAULT, HIGH }

data class Notification(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    val smallIconResId: Int,
    val tapAction: TapAction? = null,
    val autoCancel: Boolean = true,
)

sealed interface TapAction {
    data class DeepLink(val uri: String) : TapAction
}
