package com.hluhovskyi.zero.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.requireApplicationComponent
import com.hluhovskyi.zero.security.AndroidBiometricAuthenticator
import com.hluhovskyi.zero.security.BiometricAuthenticator
import com.hluhovskyi.zero.security.BiometricLockUseCase

class MainActivity : FragmentActivity() {

    private val applicationComponent by lazy { application.requireApplicationComponent() }

    private val biometricLockUseCase: BiometricLockUseCase by lazy { applicationComponent.biometricLockUseCase }
    private val biometricAuthenticator: BiometricAuthenticator by lazy { applicationComponent.biometricAuthenticator }

    private val activityComponent: AttachableViewComponent by lazy {
        applicationComponent.activityComponentBuilder
            .logging(applicationComponent.logger)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        (biometricAuthenticator as? AndroidBiometricAuthenticator)?.register(this)
        biometricLockUseCase.lock()
        setContent {
            activityComponent.AttachWithView()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            biometricLockUseCase.lock()
        }
    }

    override fun onDestroy() {
        (biometricAuthenticator as? AndroidBiometricAuthenticator)?.unregister(this)
        super.onDestroy()
    }
}
