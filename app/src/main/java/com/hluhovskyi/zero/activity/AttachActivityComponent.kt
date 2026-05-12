package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.presets.PresetsComponent
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable

class AttachActivityComponent(
    private val coroutineScope: CoroutineScope,
    presetsComponentBuilder: PresetsComponent.Builder,
) : Attachable {

    private val presetsComponent: PresetsComponent = presetsComponentBuilder
        .coroutineScope(coroutineScope)
        .build()

    override fun attach(): Closeable = presetsComponent.attachable.attach()
}
