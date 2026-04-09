package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.common.time.ZoneProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

internal class LocaleBasedDateFormatter(
    private val localeProvider: LocaleProvider,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : DateFormatter {

    private val currentYear by lazy {
        clock.localDateTime(zoneProvider.timeZone()).year
    }

    override fun format(
        date: LocalDate,
        dayConfig: DateFormatter.DayConfig,
        monthConfig: DateFormatter.MonthConfig,
        yearConfig: DateFormatter.YearConfig
    ): String {
        val patternBuilder = StringBuilder()

        when (dayConfig) {
            DateFormatter.DayConfig.Default -> patternBuilder.append("dd")
            DateFormatter.DayConfig.WithoutZero -> patternBuilder.append("d")
        }
        when (monthConfig) {
            DateFormatter.MonthConfig.Readable -> patternBuilder.append(" MMMM")
        }
        when (yearConfig) {
            DateFormatter.YearConfig.Default -> patternBuilder.append("-yyyy")
            DateFormatter.YearConfig.SkipCurrent -> if (date.year != currentYear) {
                patternBuilder.append(" yyyy")
            }
        }

        return DateTimeFormatter.ofPattern(
            patternBuilder.toString(),
            localeProvider.locale()
        ).format(date.toJavaLocalDate())
    }
}
