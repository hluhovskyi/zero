package com.hluhovskyi.zero.common

import kotlinx.datetime.LocalDate

data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date in start..end
}
