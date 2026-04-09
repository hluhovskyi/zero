package com.hluhovskyi.zero.common

import java.io.Closeable

/**
 * Lifecycle interface. [attach] starts subscriptions/coroutines and returns a [Closeable]
 * that cancels them. In Compose, `AttachWithView()` ties this to `DisposableEffect`.
 */
interface Attachable {

    fun attach(): Closeable

    object Noop : Attachable {
        override fun attach(): Closeable = Closeables.empty()
    }
}
