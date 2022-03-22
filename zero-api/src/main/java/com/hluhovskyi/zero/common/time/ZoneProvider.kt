package com.hluhovskyi.zero.common.time

import java.time.ZoneId

interface ZoneProvider {

    fun zoneId(): ZoneId
}