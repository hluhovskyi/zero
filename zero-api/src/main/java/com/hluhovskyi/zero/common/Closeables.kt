package com.hluhovskyi.zero.common

import kotlinx.coroutines.Job
import java.io.Closeable

/**
 * Factory for [Closeable] instances used in the [Attachable.attach] lifecycle pattern.
 *
 * - [empty] — no-op, for components with no cleanup.
 * - [from] — runs an arbitrary action on close.
 * - [of] — wraps a coroutine [Job]; calling `close()` cancels the job and all children.
 */
object Closeables {

    fun empty(): Closeable = EmptyCloseable

    fun from(action: () -> Unit): Closeable = ActionCloseable(action)

    inline fun of(provider: () -> Job): Closeable = JobCloseable(provider())
}

private object EmptyCloseable : Closeable {
    override fun close() = Unit
}

@PublishedApi
internal class JobCloseable(private val job: Job) : Closeable {
    override fun close() = job.cancel()
}

private class ActionCloseable(private val action: () -> Unit) : Closeable {
    override fun close() = action()
}