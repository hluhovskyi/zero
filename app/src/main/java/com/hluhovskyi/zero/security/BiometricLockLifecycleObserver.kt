package com.hluhovskyi.zero.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable

internal class BiometricLockLifecycleObserver(
    private val activity: FragmentActivity,
    private val biometricLockUseCase: BiometricLockUseCase,
) : Attachable,
    DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        biometricLockUseCase.lock()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!activity.isFinishing) {
            biometricLockUseCase.lock()
        }
    }

    override fun attach(): Closeable {
        activity.lifecycle.addObserver(this)
        return Closeables.from { activity.lifecycle.removeObserver(this) }
    }
}
