package com.hluhovskyi.zero.common.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime

interface ZonedClock : Clock, ZoneProvider {
    fun localDateTime(): LocalDateTime = now().toLocalDateTime(timeZone())
}
