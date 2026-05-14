package com.hluhovskyi.zero.security

import android.content.Context
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.config.ConfigurationRepository
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BiometricLockScope

@BiometricLockScope
@dagger.Component(
    modules = [BiometricLockComponent.Module::class],
    dependencies = [BiometricLockComponent.Dependencies::class],
)
abstract class BiometricLockComponent {

    abstract val biometricLockUseCase: BiometricLockUseCase
    abstract val biometricAuthenticator: BiometricAuthenticator
    abstract val gateComponentBuilder: BiometricLockGateComponent.Builder

    internal abstract val androidBiometricAuthenticator: AndroidBiometricAuthenticator

    interface Dependencies {
        val context: Context
        val configurationRepository: ConfigurationRepository
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBiometricLockComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BiometricLockComponent> {
        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BiometricLockScope
        fun biometricLockUseCase(
            configurationRepository: ConfigurationRepository,
        ): BiometricLockUseCase = BiometricLockUseCase(
            configurationRepository = configurationRepository,
        )

        @Provides
        @BiometricLockScope
        fun androidBiometricAuthenticator(
            context: Context,
        ): AndroidBiometricAuthenticator = AndroidBiometricAuthenticator(
            context = context,
        )

        @Provides
        @BiometricLockScope
        fun biometricAuthenticator(
            authenticator: AndroidBiometricAuthenticator,
        ): BiometricAuthenticator = authenticator

        @Provides
        @BiometricLockScope
        fun gateComponentBuilder(
            component: BiometricLockComponent,
        ): BiometricLockGateComponent.Builder = BiometricLockGateComponent.builder(
            object : BiometricLockGateComponent.Dependencies {
                override val biometricLockUseCase = component.biometricLockUseCase
                override val androidBiometricAuthenticator = component.androidBiometricAuthenticator
            },
        )
    }
}
