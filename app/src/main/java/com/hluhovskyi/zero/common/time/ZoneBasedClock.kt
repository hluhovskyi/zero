package com.hluhovskyi.zero.common.time

import java.time.ZonedDateTime

internal class ZoneBasedClock(
    private val zoneProvider: ZoneProvider
): Clock {
    override fun now(): ZonedDateTime = ZonedDateTime.now(zoneProvider.zoneId())
}