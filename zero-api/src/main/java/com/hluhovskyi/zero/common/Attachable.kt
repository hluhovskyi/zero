package com.hluhovskyi.zero.common

import java.io.Closeable

/**
 * Lifecycle interface. [attach] starts subscriptions/coroutines and returns a [Closeable]
 * that cancels them. In Compose, `AttachWithView()` ties this to `DisposableEffect`.
 */
fun interface Attachable {

    fun attach(): Closeable

    object Noop : Attachable {
        override fun attach(): Closeable = Closeables.empty()
    }

    companion object
}

fun Attachable.Companion.merge(
    vararg attachable: Attachable,
): Attachable = MergeAttachable(attachable.toList())

private class MergeAttachable(
    private val attachables: Collection<Attachable>,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        attachables.map { it.attach() },
    )
}
