package com.hluhovskyi.zero.common

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An [Attachable] that reference-counts [attach]. [onAttach] runs once on the first ref (0→1)
 * and the [Closeable] it produced is closed only when the last ref is released (1→0). Each
 * handle returned by [attach] is idempotent. Lets a single instance be held by multiple callers
 * (e.g. a session-long keep-warm ref plus a per-display `AttachWithView`).
 */
class RefCountedAttachable(
    private val onAttach: () -> Closeable,
) : Attachable {

    private val lock = Any()
    private var count = 0
    private var closeable: Closeable = Closeables.empty()

    override fun attach(): Closeable {
        synchronized(lock) {
            if (count++ == 0) {
                closeable = onAttach()
            }
        }
        val released = AtomicBoolean(false)
        return Closeables.from {
            if (released.compareAndSet(false, true)) {
                synchronized(lock) {
                    if (--count == 0) {
                        closeable.close()
                        closeable = Closeables.empty()
                    }
                }
            }
        }
    }
}
