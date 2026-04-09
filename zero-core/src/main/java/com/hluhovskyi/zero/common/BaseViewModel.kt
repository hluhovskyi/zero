package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

internal abstract class BaseViewModel(
    dispatchers: DispatcherProvider,
    logger: Logger = Logger.Noop,
) : Attachable {

    private val closeableCoroutineScope: CloseableCoroutineScope = viewModelScope(
        dispatchers = dispatchers,
        rootExceptionHandler = CoroutineExceptionHandler { _, exception -> handleException(exception) },
    )
    protected val scope: CoroutineScope
        get() = closeableCoroutineScope

    override fun attach(): Closeable {
        attachOnMain()
        return closeableCoroutineScope
    }

    protected open fun attachOnMain() {
    }

    protected open fun handleException(throwable: Throwable): Unit = throw throwable
}
