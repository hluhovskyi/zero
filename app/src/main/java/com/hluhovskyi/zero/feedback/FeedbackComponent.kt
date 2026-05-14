package com.hluhovskyi.zero.feedback

import android.content.Context
import android.hardware.SensorManager
import com.hluhovskyi.zero.BuildConfig
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.activity.navigation.NavigatorScope
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import dagger.Provides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
internal abstract class FeedbackComponent : Attachable {

    abstract val navigationEntry: NavigatorEntry

    protected abstract val inMemoryBreadcrumbs: InMemoryBreadcrumbs
    protected abstract val shakeDetector: ShakeDetector

    override fun attach(): Closeable = Closeables.merge(
        inMemoryBreadcrumbs.attach(),
        shakeDetector.attach(),
    )

    interface Dependencies {
        val context: Context
        val clock: Clock
        val navigator: Navigator
        val navigatorScope: NavigatorScope
        val feedbackService: FeedbackService
        val deviceInfo: DeviceInfo
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
        fun routes(navigator: Navigator): Flow<String> = navigator.state.map { it.destination.route }

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
            navigator: Navigator,
        ): ShakeDetector = ShakeDetector(
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
            onShake = { navigator.navigateTo(Destinations.Feedback) },
        )

        @Provides
        @FeedbackScope
        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @Provides
        @FeedbackScope
        fun errorMessageProvider(context: Context): () -> String = { context.getString(R.string.feedback_error_generic) }

        @Provides
        @FeedbackScope
        fun onFeedbackSubmittedHandler(navigator: Navigator): OnFeedbackSubmittedHandler = OnFeedbackSubmittedHandler { navigator.back() }

        @Provides
        @FeedbackScope
        fun navigationEntry(
            sheetComponentProvider: Provider<FeedbackSheetComponent>,
            navigatorScope: NavigatorScope,
        ): NavigatorEntry = navigatorScope.component(
            destination = Destinations.Feedback,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            sheetComponentProvider.get()
        }
    }
}
