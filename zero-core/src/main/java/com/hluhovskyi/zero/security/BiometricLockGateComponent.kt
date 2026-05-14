package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
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

    abstract val useCase: BiometricLockUseCase

    internal abstract val viewModel: BiometricLockGateViewModel

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val biometricLockUseCase: BiometricLockUseCase
        val biometricAuthenticator: BiometricAuthenticator
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBiometricLockGateComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BiometricLockGateComponent> {
        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BiometricLockGateScope
        fun viewModel(
            biometricLockUseCase: BiometricLockUseCase,
            biometricAuthenticator: BiometricAuthenticator,
        ): BiometricLockGateViewModel = DefaultBiometricLockGateViewModel(
            biometricLockUseCase = biometricLockUseCase,
            biometricAuthenticator = biometricAuthenticator,
        )

        @Provides
        @BiometricLockGateScope
        fun viewProvider(viewModel: BiometricLockGateViewModel): ViewProvider = BiometricLockGateViewProvider(viewModel = viewModel)
    }
}
