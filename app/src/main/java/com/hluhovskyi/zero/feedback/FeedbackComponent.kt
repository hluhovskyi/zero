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
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.time.Clock
import dagger.Provides
import kotlinx.coroutines.flow.map
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
internal annotation class FeedbackScope

private const val TAG = "FeedbackComponent"

@FeedbackScope
@dagger.Component(
    dependencies = [FeedbackComponent.Dependencies::class],
    modules = [FeedbackComponent.Module::class],
)
internal abstract class FeedbackComponent : Attachable {

    abstract val navigationEntry: NavigatorEntry

    protected abstract val inMemoryBreadcrumbs: InMemoryBreadcrumbs
    protected abstract val shakeFeedbackEntry: ShakeFeedbackEntry

    override fun attach(): Closeable = Closeables.merge(
        inMemoryBreadcrumbs.attach(),
        shakeFeedbackEntry.attach(),
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
        fun inMemoryBreadcrumbs(
            navigator: Navigator,
            clock: Clock,
        ): InMemoryBreadcrumbs = InMemoryBreadcrumbs(
            routes = navigator.state.map { it.destination.route },
            clock = clock,
        )

        @Provides
        @FeedbackScope
        fun breadcrumbs(impl: InMemoryBreadcrumbs): Breadcrumbs = impl

        @Provides
        @FeedbackScope
        fun shakeFeedbackEntry(
            context: Context,
            navigator: Navigator,
        ): ShakeFeedbackEntry = ShakeFeedbackEntry(
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
            navigator = navigator,
        )

        @Provides
        @FeedbackScope
        fun feedbackSheetComponentFactory(
            feedbackService: FeedbackService,
            breadcrumbs: Breadcrumbs,
            deviceInfo: DeviceInfo,
            clock: Clock,
        ): FeedbackSheetComponent.Factory {
            val dependencies = object : FeedbackSheetComponent.Dependencies {
                override val feedbackService: FeedbackService = feedbackService
                override val breadcrumbs: Breadcrumbs = breadcrumbs
                override val deviceInfo: DeviceInfo = deviceInfo
                override val clock: Clock = clock
            }
            return FeedbackSheetComponent.factory(dependencies)
        }

        @Provides
        @FeedbackScope
        fun navigationEntry(
            factory: FeedbackSheetComponent.Factory,
            navigatorScope: NavigatorScope,
            context: Context,
        ): NavigatorEntry = navigatorScope.component(
            destination = Destinations.Feedback,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            factory.create(
                isDebugBuild = BuildConfig.DEBUG,
                errorMessageProvider = { context.getString(R.string.feedback_error_generic) },
                onFeedbackSubmittedHandler = { navigator.back() },
            )
        }
    }
}
