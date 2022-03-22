package com.hluhovskyi.zero.common.time

import java.time.LocalDateTime
import java.time.ZonedDateTime

interface Clock {

    fun now(): ZonedDateTime
}

fun Clock.localDateTime(): LocalDateTime = now().toLocalDateTime()