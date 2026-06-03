package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import java.io.Closeable

internal abstract class BaseViewModel(
    private val dispatchers: DispatcherProvider,
    logger: Logger = Logger.Noop,
) : Attachable {

    private var closeableCoroutineScope: CloseableCoroutineScope = newScope()
    protected val scope: CoroutineScope
        get() = closeableCoroutineScope

    private val refCounted = RefCountedAttachable {
        // The scope is cancelled when the last ref is released; recreate it so a view model
        // can be re-attached and resume its work (e.g. a detail screen revisited after the
        // user navigated away to add a transaction).
        if (!closeableCoroutineScope.isActive) {
            closeableCoroutineScope = newScope()
        }
        attachOnMain()
        closeableCoroutineScope
    }

    override fun attach(): Closeable = refCounted.attach()

    protected open fun attachOnMain() {
    }

    protected open fun handleException(throwable: Throwable): Unit = throw throwable

    private fun newScope(): CloseableCoroutineScope = viewModelScope(
        dispatchers = dispatchers,
        rootExceptionHandler = CoroutineExceptionHandler { _, exception -> handleException(exception) },
    )
}
