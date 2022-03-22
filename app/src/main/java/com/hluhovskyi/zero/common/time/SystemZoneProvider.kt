package com.hluhovskyi.zero.common.time

import java.time.ZoneId

internal object SystemZoneProvider : ZoneProvider{
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}