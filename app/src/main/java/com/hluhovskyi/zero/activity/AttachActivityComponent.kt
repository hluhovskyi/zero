package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.presets.PresetsComponent
import com.hluhovskyi.zero.security.BiometricLockComponent
import java.io.Closeable

class AttachActivityComponent(
    private val presetsComponent: PresetsComponent,
    private val biometricLockComponent: BiometricLockComponent,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        presetsComponent.attachable.attach(),
        biometricLockComponent.attach(),
    )
}
