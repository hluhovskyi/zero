package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

internal class ZoneBasedClock(
    private val zoneProvider: ZoneProvider,
) : ZonedClock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
    override fun timeZone(): TimeZone = zoneProvider.timeZone()
}
