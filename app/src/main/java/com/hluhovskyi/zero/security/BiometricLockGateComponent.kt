package com.hluhovskyi.zero.security

import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BiometricLockGateScope

private const val TAG = "BiometricLockGateComponent"

@BiometricLockGateScope
@dagger.Component(
    modules = [BiometricLockGateComponent.Module::class],
    dependencies = [BiometricLockGateComponent.Dependencies::class],
)
abstract class BiometricLockGateComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: BiometricLockGateViewModel
    internal abstract val activity: FragmentActivity
    internal abstract val androidBiometricAuthenticator: AndroidBiometricAuthenticator

    override fun attach(): Closeable {
        androidBiometricAuthenticator.register(activity)
        val viewModelCloseable = viewModel.attach()
        return Closeables.from {
            viewModelCloseable.close()
            androidBiometricAuthenticator.unregister(activity)
        }
    }

    interface Dependencies {
        val biometricLockUseCase: BiometricLockUseCase
        val androidBiometricAuthenticator: AndroidBiometricAuthenticator
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBiometricLockGateComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BiometricLockGateComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun activity(activity: FragmentActivity): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BiometricLockGateScope
        fun viewModel(
            biometricLockUseCase: BiometricLockUseCase,
            authenticator: AndroidBiometricAuthenticator,
        ): BiometricLockGateViewModel = BiometricLockGateViewModel(
            biometricLockUseCase = biometricLockUseCase,
            biometricAuthenticator = authenticator,
        )

        @Provides
        @BiometricLockGateScope
        fun viewProvider(viewModel: BiometricLockGateViewModel): ViewProvider = BiometricLockGateViewProvider(viewModel = viewModel)
    }
}
