package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.presets.PresetsComponent
import java.io.Closeable

class AttachActivityComponent(
    private val presetsComponent: PresetsComponent,
) : Attachable {

    override fun attach(): Closeable = presetsComponent.attachable.attach()
}
