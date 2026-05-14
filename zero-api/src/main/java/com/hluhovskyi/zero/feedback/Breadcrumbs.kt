package com.hluhovskyi.zero.feedback

import kotlinx.datetime.Instant

interface Breadcrumbs {

    fun log(message: String)

    fun snapshot(): Snapshot

    data class Snapshot(
        val navigation: List<Entry>,
        val breadcrumbs: List<Entry>,
    )

    data class Entry(
        val timestamp: Instant,
        val message: String,
    )
}
