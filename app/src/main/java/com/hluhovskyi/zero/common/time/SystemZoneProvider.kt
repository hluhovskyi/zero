package com.hluhovskyi.zero.common.time

import kotlinx.datetime.TimeZone

internal object SystemZoneProvider : ZoneProvider {
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}
