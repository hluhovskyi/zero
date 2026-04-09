package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Injectable time provider. Always use this instead of Clock.System.now() — keeps code testable
 * with fixed clocks.
 */
interface Clock {
    fun now(): Instant
}

/** Convenience extension — requires explicit timezone from ZoneProvider. */
fun Clock.localDateTime(timeZone: TimeZone): LocalDateTime = now().toLocalDateTime(timeZone)
