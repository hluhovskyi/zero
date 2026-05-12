package com.hluhovskyi.zero.common.time

import kotlinx.datetime.LocalDateTime

interface ZonedClock : Clock, ZoneProvider {
    fun localDateTime(): LocalDateTime = now().toLocalDateTime(timeZone())
}
