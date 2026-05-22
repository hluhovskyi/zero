package com.hluhovskyi.zero

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable

class AttachApplicationComponent(
    private val crashComponent: CrashComponent,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        crashComponent.attachable.attach(),
    )
}
