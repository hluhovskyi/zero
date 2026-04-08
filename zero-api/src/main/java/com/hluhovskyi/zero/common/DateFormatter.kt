package com.hluhovskyi.zero.common

import kotlinx.datetime.LocalDate

interface DateFormatter {

    fun format(
        date: LocalDate,
        dayConfig: DayConfig,
        monthConfig: MonthConfig,
        yearConfig: YearConfig
    ): String

    enum class DayConfig {
        Default,
        WithoutZero,
    }

    enum class MonthConfig {
        Readable,
    }

    enum class YearConfig {
        Default,
        SkipCurrent,
    }
}
