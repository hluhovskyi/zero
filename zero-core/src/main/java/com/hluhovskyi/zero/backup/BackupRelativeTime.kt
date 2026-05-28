package com.hluhovskyi.zero.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hluhovskyi.zero.R
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Formats `at` as "just now" / "N minutes ago" / "N hours ago" / "N days ago". Recomputes only when `at` changes. */
@Composable
internal fun rememberBackupRelativeTime(at: LocalDateTime): String {
    val diff = remember(at) {
        val now = Clock.System.now()
        val atInstant = at.toInstant(TimeZone.currentSystemDefault())
        now - atInstant
    }
    return when {
        diff < 1.minutes -> stringResource(R.string.backup_relative_just_now)
        diff < 1.hours -> {
            val m = diff.inWholeMinutes.toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.backup_relative_minutes_ago, m, m)
        }
        diff < 1.days -> {
            val h = diff.inWholeHours.toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.backup_relative_hours_ago, h, h)
        }
        else -> {
            val d = diff.inWholeDays.toInt().coerceAtLeast(1)
            pluralStringResource(R.plurals.backup_relative_days_ago, d, d)
        }
    }
}
