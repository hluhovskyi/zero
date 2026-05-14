package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import java.io.Closeable

private const val TAG = "FeedbackComponent"

class FeedbackComponent private constructor(
    feedbackService: FeedbackService,
    breadcrumbs: Breadcrumbs,
    deviceInfo: DeviceInfo,
    clock: Clock,
    isDebugBuild: Boolean,
    errorMessageProvider: () -> String,
    onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler,
) : AttachableViewComponent {

    override val tag: String = TAG

    private val viewModel: FeedbackViewModel by lazy {
        DefaultFeedbackViewModel(
            feedbackService = feedbackService,
            breadcrumbs = breadcrumbs,
            reportFormatter = FeedbackReportFormatter(
                deviceInfo = deviceInfo,
                isDebugBuild = isDebugBuild,
                clock = clock,
            ),
            onFeedbackSubmittedHandler = onFeedbackSubmittedHandler,
            errorMessageProvider = errorMessageProvider,
            deviceInfo = deviceInfo,
        )
    }

    override val viewProvider: ViewProvider by lazy {
        FeedbackViewProvider(viewModel = viewModel)
    }

    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val feedbackService: FeedbackService
        val breadcrumbs: Breadcrumbs
        val deviceInfo: DeviceInfo
        val clock: Clock
    }

    class Factory(private val dependencies: Dependencies) {

        fun create(
            isDebugBuild: Boolean,
            errorMessageProvider: () -> String,
            onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler = OnFeedbackSubmittedHandler.Noop,
        ): FeedbackComponent = FeedbackComponent(
            feedbackService = dependencies.feedbackService,
            breadcrumbs = dependencies.breadcrumbs,
            deviceInfo = dependencies.deviceInfo,
            clock = dependencies.clock,
            isDebugBuild = isDebugBuild,
            errorMessageProvider = errorMessageProvider,
            onFeedbackSubmittedHandler = onFeedbackSubmittedHandler,
        )
    }

    companion object {
        fun factory(dependencies: Dependencies): Factory = Factory(dependencies)
    }
}
