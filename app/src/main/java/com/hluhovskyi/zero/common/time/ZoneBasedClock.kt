package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant

internal class ZoneBasedClock(
    private val zoneProvider: ZoneProvider,
) : Clock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
}
