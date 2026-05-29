package com.hluhovskyi.zero.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * [Attachable] that owns a [CoroutineScope] active between attach and close. Subclasses launch
 * coroutines in [scope] from [onAttach]; the scope cancels on close.
 */
abstract class ScopedAttachable : Attachable {

    protected abstract val coroutineContext: CoroutineContext

    protected lateinit var scope: CoroutineScope
        private set

    override fun attach(): Closeable {
        val supervisor = SupervisorJob()
        scope = CoroutineScope(supervisor + coroutineContext)
        onAttach()
        return Closeables.from { supervisor.cancel() }
    }

    protected open fun onAttach() = Unit
}
