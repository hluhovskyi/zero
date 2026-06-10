package com.hluhovskyi.zero.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Coarse "how long ago" bucket for the last successful backup. Derived in ViewModels via [of]
 * (time math is a derivation, not formatting); the view only pattern-matches and formats.
 */
sealed interface RelativeAge {
    object JustNow : RelativeAge
    data class Minutes(val count: Int) : RelativeAge
    data class Hours(val count: Int) : RelativeAge
    data class Days(val count: Int) : RelativeAge

    companion object {
        fun of(at: LocalDateTime, clock: Clock): RelativeAge {
            val diff = clock.now() - at.toInstant(TimeZone.currentSystemDefault())
            return when {
                diff < 1.minutes -> JustNow
                diff < 1.hours -> Minutes(diff.inWholeMinutes.toInt().coerceAtLeast(1))
                diff < 1.days -> Hours(diff.inWholeHours.toInt().coerceAtLeast(1))
                else -> Days(diff.inWholeDays.toInt().coerceAtLeast(1))
            }
        }
    }
}

/** Formats as "just now" / "N minutes ago" / "N hours ago" / "N days ago". */
@Composable
internal fun RelativeAge.toLabel(): String = when (this) {
    RelativeAge.JustNow -> stringResource(R.string.backup_relative_just_now)
    is RelativeAge.Minutes -> pluralStringResource(R.plurals.backup_relative_minutes_ago, count, count)
    is RelativeAge.Hours -> pluralStringResource(R.plurals.backup_relative_hours_ago, count, count)
    is RelativeAge.Days -> pluralStringResource(R.plurals.backup_relative_days_ago, count, count)
}
