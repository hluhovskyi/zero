package com.hluhovskyi.zero.feedback

import android.content.Context
import android.hardware.SensorManager
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import dagger.Provides
import kotlinx.coroutines.flow.Flow
import java.io.Closeable
import javax.inject.Provider
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
internal annotation class FeedbackScope

@FeedbackScope
@dagger.Component(
    dependencies = [FeedbackComponent.Dependencies::class],
    modules = [FeedbackComponent.Module::class],
)
abstract class FeedbackComponent : Attachable {

    abstract val sheetComponentProvider: Provider<FeedbackSheetComponent>

    protected abstract val inMemoryBreadcrumbs: InMemoryBreadcrumbs
    protected abstract val shakeDetector: ShakeDetector

    override fun attach(): Closeable = Closeables.merge(
        inMemoryBreadcrumbs.attach(),
        shakeDetector.attach(),
    )

    interface Dependencies {
        val context: Context
        val clock: Clock
        val feedbackService: FeedbackService
        val deviceInfo: DeviceInfo
        val routes: Flow<String>
        val onShakeDetected: () -> Unit
        val isDebugBuild: Boolean
        val errorMessageProvider: () -> String
        val onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerFeedbackComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<FeedbackComponent> {
        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @FeedbackScope
        fun inMemoryBreadcrumbs(
            routes: Flow<String>,
            clock: Clock,
        ): InMemoryBreadcrumbs = InMemoryBreadcrumbs(
            routes = routes,
            clock = clock,
        )

        @Provides
        @FeedbackScope
        fun breadcrumbs(impl: InMemoryBreadcrumbs): Breadcrumbs = impl

        @Provides
        @FeedbackScope
        fun shakeDetector(
            context: Context,
            onShakeDetected: () -> Unit,
        ): ShakeDetector = ShakeDetector(
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
            onShake = onShakeDetected,
        )
    }
}
