package com.hluhovskyi.zero.common

import java.time.LocalDate

interface DateFormatter {

    fun format(
        date: LocalDate,
        dayConfig: DayConfig,
        monthConfig: MonthConfig,
        yearConfig: YearConfig
    ): String

    enum class DayConfig {
        /**
         * Format day as is, e.g. 01 or 12.
         */
        Default,

        /**
         * Format day without zero in beginning, e.g. 1 May or 12 February.
         */
        WithoutZero,
    }

    enum class MonthConfig {
        /**
         * Format month in readable form, e.g. May.
         */
        Readable,
    }

    enum class YearConfig {
        /**
         * Year formatted as is, e.g. 2022.
         */
        Default,

        /**
         * In case formatter year equal to the current one, it would be skipped.
         * E.g. current date is 24.02.2022 and date to format is 15.01.2022, result would be 15.01
         */
        SkipCurrent,
    }
}
