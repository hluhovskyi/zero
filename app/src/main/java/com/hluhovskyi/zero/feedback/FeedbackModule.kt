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
import com.hluhovskyi.zero.activity.screens.MainActivityScreenScope
import com.hluhovskyi.zero.common.time.Clock
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.map

@Module
internal object FeedbackModule {

    @Provides
    @MainActivityScreenScope
    fun inMemoryBreadcrumbs(
        navigator: Navigator,
        clock: Clock,
    ): InMemoryBreadcrumbs = InMemoryBreadcrumbs(
        routes = navigator.state.map { it.destination.route },
        clock = clock,
    )

    @Provides
    @MainActivityScreenScope
    fun breadcrumbs(impl: InMemoryBreadcrumbs): Breadcrumbs = impl

    @Provides
    @MainActivityScreenScope
    fun shakeFeedbackEntry(
        context: Context,
        navigator: Navigator,
    ): ShakeFeedbackEntry = ShakeFeedbackEntry(
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
        navigator = navigator,
    )

    @Provides
    @MainActivityScreenScope
    fun feedbackComponentFactory(
        feedbackService: FeedbackService,
        breadcrumbs: Breadcrumbs,
        deviceInfo: DeviceInfo,
        clock: Clock,
    ): FeedbackComponent.Factory {
        val dependencies = object : FeedbackComponent.Dependencies {
            override val feedbackService: FeedbackService = feedbackService
            override val breadcrumbs: Breadcrumbs = breadcrumbs
            override val deviceInfo: DeviceInfo = deviceInfo
            override val clock: Clock = clock
        }
        return FeedbackComponent.factory(dependencies)
    }

    @Provides
    @IntoSet
    @MainActivityScreenScope
    fun feedbackNavigationEntry(
        factory: FeedbackComponent.Factory,
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
