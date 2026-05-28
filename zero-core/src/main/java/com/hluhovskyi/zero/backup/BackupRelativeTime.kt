package com.hluhovskyi.zero.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hluhovskyi.zero.R
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Formats `at` as "just now" / "N minutes ago" / "N hours ago" / "N days ago".
 *
 * Computed once per `at` change; does not tick. Acceptable because the BackupUseCase emits
 * a fresh state on every transition, which retriggers the surrounding composable.
 */
@Composable
internal fun rememberBackupRelativeTime(at: LocalDateTime): String {
    val context = LocalContext.current
    return remember(at) {
        val now = Clock.System.now()
        val atInstant = at.toInstant(TimeZone.currentSystemDefault())
        val diff = now - atInstant
        when {
            diff < 1.minutes -> context.getString(R.string.backup_relative_just_now)
            diff < 1.hours -> {
                val m = diff.inWholeMinutes.toInt().coerceAtLeast(1)
                context.resources.getQuantityString(R.plurals.backup_relative_minutes_ago, m, m)
            }
            diff < 1.days -> {
                val h = diff.inWholeHours.toInt().coerceAtLeast(1)
                context.resources.getQuantityString(R.plurals.backup_relative_hours_ago, h, h)
            }
            else -> {
                val d = diff.inWholeDays.toInt().coerceAtLeast(1)
                context.resources.getQuantityString(R.plurals.backup_relative_days_ago, d, d)
            }
        }
    }
}
