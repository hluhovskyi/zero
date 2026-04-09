package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

internal fun viewModelScope(
    dispatchers: DispatcherProvider,
    rootExceptionHandler: CoroutineExceptionHandler? = null,
): CloseableCoroutineScope = ViewModelCoroutineScope(dispatchers, rootExceptionHandler)

private class ViewModelCoroutineScope(
    dispatchers: DispatcherProvider,
    rootExceptionHandler: CoroutineExceptionHandler?,
) : CloseableCoroutineScope {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = if (rootExceptionHandler == null) {
        dispatchers.main() + supervisorJob
    } else {
        dispatchers.main() + supervisorJob + rootExceptionHandler
    }

    override fun close() {
        supervisorJob.cancel()
    }
}
