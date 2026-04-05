package com.hluhovskyi.zero.common.time

import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Injectable time provider. Always use this instead of `LocalDateTime.now()` — keeps code testable
 * with fixed clocks.
 */
interface Clock {

    fun now(): ZonedDateTime
}

/** Convenience extension — the most common usage throughout the codebase. */
fun Clock.localDateTime(): LocalDateTime = now().toLocalDateTime()