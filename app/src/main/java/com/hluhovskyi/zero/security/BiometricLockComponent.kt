package com.hluhovskyi.zero.security

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.config.ConfigurationRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BiometricLockScope

@BiometricLockScope
@dagger.Component(
    modules = [BiometricLockComponent.Module::class],
    dependencies = [BiometricLockComponent.Dependencies::class],
)
abstract class BiometricLockComponent : Attachable {

    abstract val biometricLockUseCase: BiometricLockUseCase
    abstract val biometricAuthenticator: BiometricAuthenticator
    abstract val gateComponent: BiometricLockGateComponent.Builder

    protected abstract val lifecycleObserver: Attachable

    override fun attach(): Closeable = lifecycleObserver.attach()

    interface Dependencies {
        val context: Context
        val configurationRepository: ConfigurationRepository
        val clock: Clock
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBiometricLockComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BiometricLockComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun activity(activity: FragmentActivity): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BiometricLockScope
        fun biometricLockUseCase(
            configurationRepository: ConfigurationRepository,
            clock: Clock,
        ): BiometricLockUseCase = DefaultBiometricLockUseCase(
            configurationRepository = configurationRepository,
            clock = clock,
        )

        @Provides
        @BiometricLockScope
        fun biometricAuthenticator(
            context: Context,
            activity: FragmentActivity,
        ): BiometricAuthenticator = AndroidBiometricAuthenticator(
            context = context,
            activity = activity,
        )

        @Provides
        @BiometricLockScope
        fun lifecycleObserver(
            activity: FragmentActivity,
            biometricLockUseCase: BiometricLockUseCase,
        ): Attachable = BiometricLockLifecycleObserver(
            activity = activity,
            biometricLockUseCase = biometricLockUseCase,
        )

        @Provides
        fun gateComponent(
            biometricLockUseCase: BiometricLockUseCase,
            biometricAuthenticator: BiometricAuthenticator,
        ): BiometricLockGateComponent.Builder = BiometricLockGateComponent.builder(
            object : BiometricLockGateComponent.Dependencies {
                override val biometricLockUseCase = biometricLockUseCase
                override val biometricAuthenticator = biometricAuthenticator
            },
        )
    }
}
