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
    fun timeZone(): TimeZone
}

/** Convenience extension — the most common usage throughout the codebase. */
fun Clock.localDateTime(): LocalDateTime = now().toLocalDateTime(timeZone())
