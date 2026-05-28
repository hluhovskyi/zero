package com.hluhovskyi.zero.backup

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BackupDeepLinkSignal {

    private val mutable = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val requests: SharedFlow<Unit> = mutable.asSharedFlow()

    fun request() {
        mutable.tryEmit(Unit)
    }
}
