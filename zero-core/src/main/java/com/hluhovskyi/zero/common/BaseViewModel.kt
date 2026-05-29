package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

internal abstract class BaseViewModel(
    dispatchers: DispatcherProvider,
    logger: Logger = Logger.Noop,
) : ScopedAttachable() {

    override val coroutineContext: CoroutineContext =
        dispatchers.main() + CoroutineExceptionHandler { _, exception -> handleException(exception) }

    private val refCounted = RefCountedAttachable(::openScope)

    final override fun attach(): Closeable = refCounted.attach()

    final override fun onAttach() = attachOnMain()

    private fun openScope(): Closeable = super.attach()

    protected open fun attachOnMain() {
    }

    protected open fun handleException(throwable: Throwable): Unit = throw throwable
}
