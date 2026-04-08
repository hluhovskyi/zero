package com.hluhovskyi.zero.common.time

import kotlinx.datetime.TimeZone

interface ZoneProvider {
    fun timeZone(): TimeZone
}
